package com.ploybot.promptshield.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.proxy.client.AiProviderClient;
import com.ploybot.promptshield.proxy.client.AiProviderClient.ProviderResponse;
import com.ploybot.promptshield.proxy.service.ProxyService;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProxyServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern TAG = Pattern.compile("~REDACTED:[A-Z_0-9]+#[a-f0-9]+~");

    @Mock
    private AiProviderClient aiClient;

    private InMemoryStorageService storage;
    private ObfuscationEngine engine;
    private ProxyService proxyService;

    @BeforeEach
    void setUp() {
        storage = new InMemoryStorageService();
        engine = new ObfuscationEngine(new ObfuscationConfig(), storage);
        proxyService = new ProxyService(aiClient, engine, true, true);
    }

    @Test
    void obfuscatesMessagesAndKeepsUnknownFields() throws Exception {
        String request = """
                {"model":"gpt-4o-mini","top_p":0.3,"response_format":{"type":"json_object"},"seed":7,
                 "messages":[
                   {"role":"system","content":"Atiendes a juan@example.com"},
                   {"role":"user","content":[{"type":"text","text":"Mi DNI es 12345678Z"},{"type":"image_url","image_url":{"url":"https://x/y.png"}}]},
                   {"role":"assistant","content":null,"tool_calls":[{"id":"c1","type":"function","function":{"name":"find","arguments":"{\\"dni\\":\\"12345678Z\\"}"}}]},
                   {"role":"tool","tool_call_id":"c1","content":"{\\"email\\":\\"juan@example.com\\"}"}
                 ]}""";
        AtomicReference<String> sent = new AtomicReference<>();
        when(aiClient.chatCompletion(anyString(), any())).thenAnswer(invocation -> {
            sent.set(invocation.getArgument(0));
            return new ProviderResponse(200, "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"OK\"}}]}");
        });

        proxyService.chatCompletion(request, "Bearer sk-client");

        String body = sent.get();
        assertFalse(body.contains("12345678Z"), body);
        assertFalse(body.contains("juan@example.com"), body);
        JsonNode json = MAPPER.readTree(body);
        assertEquals(0.3, json.get("top_p").asDouble());
        assertEquals("json_object", json.at("/response_format/type").asText());
        assertEquals(7, json.get("seed").asInt());
        assertEquals("https://x/y.png", json.at("/messages/1/content/1/image_url/url").asText());
        assertTrue(json.at("/messages/0/content").asText().contains("REDACTED:TYPE#HASH"), "instructions merged into the system message");
        assertEquals(4, json.get("messages").size(), "no extra system message when one exists");
        verify(aiClient).chatCompletion(anyString(), eq("Bearer sk-client"));
    }

    @Test
    void restoresContentAndToolCallArguments() throws Exception {
        when(aiClient.chatCompletion(anyString(), any())).thenAnswer(invocation -> {
            String tag = lastTag(invocation.getArgument(0));
            return new ProviderResponse(200, """
                    {"id":"x","choices":[{"index":0,"finish_reason":"tool_calls","message":{"role":"assistant",
                     "content":"Enviando a %1$s","tool_calls":[{"id":"c1","type":"function","function":{"name":"send","arguments":"{\\"to\\":\\"%1$s\\"}"}}]}}],
                     "usage":{"total_tokens":3}}""".formatted(tag));
        });

        ProviderResponse response = proxyService.chatCompletion(userRequest("Escribe a juan@example.com", false), null);

        JsonNode json = MAPPER.readTree(response.body());
        assertEquals("Enviando a juan@example.com", json.at("/choices/0/message/content").asText());
        assertEquals("{\"to\":\"juan@example.com\"}", json.at("/choices/0/message/tool_calls/0/function/arguments").asText());
        assertEquals(3, json.at("/usage/total_tokens").asInt());
        assertEquals(0, storage.size(), "per-request mappings must be released");
    }

    @Test
    void providerErrorsArePassedThrough() {
        when(aiClient.chatCompletion(anyString(), any()))
                .thenReturn(new ProviderResponse(401, "{\"error\":{\"message\":\"bad key\"}}"));

        ProviderResponse response = proxyService.chatCompletion(userRequest("Hola", false), "Bearer bad");

        assertEquals(401, response.status());
        assertEquals("{\"error\":{\"message\":\"bad key\"}}", response.body());
    }

    @Test
    void streamingRestoresDeltaContentAndToolArgumentsSplitAcrossChunks() throws Exception {
        when(aiClient.chatCompletionStream(anyString(), any())).thenAnswer(invocation -> {
            String tag = lastTag(invocation.getArgument(0));
            int middle = tag.length() / 2;
            String args = "{\"to\":\"" + tag + "\"}";
            return Flux.just(
                    chunk("{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Email: " + tag.substring(0, middle) + "\"}}"),
                    chunk("{\"index\":0,\"delta\":{\"content\":\"" + tag.substring(middle) + " listo\"}}"),
                    chunk("{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"c1\",\"function\":{\"name\":\"send\",\"arguments\":"
                            + MAPPER.writeValueAsString(args.substring(0, 12)) + "}}]}}"),
                    chunk("{\"index\":0,\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":"
                            + MAPPER.writeValueAsString(args.substring(12)) + "}}]}}"),
                    chunk("{\"index\":0,\"delta\":{},\"finish_reason\":\"tool_calls\"}"));
        });

        List<String> chunks = proxyService.chatCompletionStream(userRequest("Mi email es juan@example.com", true), null)
                .collectList().block();

        StringBuilder content = new StringBuilder();
        StringBuilder arguments = new StringBuilder();
        for (String chunk : chunks) {
            JsonNode delta = MAPPER.readTree(chunk).at("/choices/0/delta");
            content.append(delta.path("content").asText(""));
            delta.path("tool_calls").forEach(call -> arguments.append(call.at("/function/arguments").asText("")));
        }
        assertEquals("Email: juan@example.com listo", content.toString());
        assertEquals("{\"to\":\"juan@example.com\"}", arguments.toString());
        assertEquals(0, storage.size());
    }

    @Test
    void streamEndingMidTagFlushesTheRemainder() throws Exception {
        when(aiClient.chatCompletionStream(anyString(), any()))
                .thenReturn(Flux.just(chunk("{\"index\":0,\"delta\":{\"content\":\"Fin ~REDACTED:EM\"}}")));

        List<String> chunks = proxyService.chatCompletionStream(userRequest("Hola", true), null).collectList().block();

        String content = chunks.stream()
                .map(chunk -> readTree(chunk).at("/choices/0/delta/content").asText(""))
                .reduce("", String::concat);
        assertEquals("Fin ~REDACTED:EM", content);
    }

    @Test
    void tagsFromAnotherRequestAreNotRestored() {
        // Tags minted for one proxied request must not be restorable in another:
        // otherwise a prompt injection could exfiltrate other users' data
        String foreignTag = engine.forScope("other-user").obfuscate("victima@example.com");
        when(aiClient.chatCompletion(anyString(), any())).thenReturn(new ProviderResponse(200,
                "{\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"Datos: " + foreignTag + "\"}}]}"));

        ProviderResponse response = proxyService.chatCompletion(userRequest("Hola", false), null);

        assertFalse(response.body().contains("victima@example.com"));
    }

    @Test
    void noSystemPromptWhenInjectionIsDisabled() throws Exception {
        ProxyService noInjection = new ProxyService(aiClient, engine, false, true);
        AtomicReference<String> sent = new AtomicReference<>();
        when(aiClient.chatCompletion(anyString(), any())).thenAnswer(invocation -> {
            sent.set(invocation.getArgument(0));
            return new ProviderResponse(200, "{\"choices\":[]}");
        });

        noInjection.chatCompletion(userRequest("Send email to juan@example.com", false), null);

        JsonNode messages = MAPPER.readTree(sent.get()).get("messages");
        assertEquals(1, messages.size());
        assertTrue(messages.at("/0/content").asText().contains("~REDACTED:EMAIL#"));
    }

    @Test
    void invalidRequestsAreRejected() {
        assertThrows(ProxyService.InvalidRequestException.class, () -> proxyService.chatCompletion("{\"model\":\"x\"}", null));
        assertThrows(ProxyService.InvalidRequestException.class, () -> ProxyService.isStreamRequest("not json"));
    }

    private static String userRequest(String text, boolean stream) {
        return "{\"model\":\"gpt-4o-mini\",\"stream\":" + stream + ",\"messages\":[{\"role\":\"user\",\"content\":"
                + quote(text) + "}]}";
    }

    private static String chunk(String choice) {
        return "{\"id\":\"chatcmpl-1\",\"object\":\"chat.completion.chunk\",\"model\":\"gpt-4o-mini\",\"choices\":[" + choice + "]}";
    }

    /** The user's tag: the injected instructions come first and contain an example tag. */
    private static String lastTag(String text) {
        Matcher matcher = TAG.matcher(text);
        String tag = null;
        while (matcher.find()) {
            tag = matcher.group();
        }
        assertNotNull(tag, () -> "no tag in: " + text);
        return tag;
    }

    private static String quote(String text) {
        try {
            return MAPPER.writeValueAsString(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
