package com.ploybot.promptshield.proxy;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.proxy.client.AiProviderClient;
import com.ploybot.promptshield.proxy.controller.ProxyAuthorization;
import com.ploybot.promptshield.proxy.controller.ProxyController;
import com.ploybot.promptshield.proxy.service.ProxyService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Controller and HTTP client against a fake OpenAI-compatible provider. */
class ProxyEndToEndTest {

    private static final Pattern TAG = Pattern.compile("~REDACTED:[A-Z_0-9]+#[a-f0-9]+~");

    private HttpServer provider;
    private final AtomicReference<String> receivedAuthorization = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();

    @BeforeEach
    void startProvider() throws Exception {
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/v1/chat/completions", exchange -> {
            receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            receivedBody.set(body);
            // The last tag is the user's; the injected instructions contain an example one
            Matcher matcher = TAG.matcher(body);
            String tag = "";
            while (matcher.find()) {
                tag = matcher.group();
            }
            int middle = tag.length() / 2;
            String sse = "data: " + chunk("Tu email es " + tag.substring(0, middle)) + "\n\n"
                    + "data: " + chunk(tag.substring(middle)) + "\n\n"
                    + "data: [DONE]\n\n";
            byte[] bytes = sse.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        provider.start();
    }

    @AfterEach
    void stopProvider() {
        provider.stop(0);
    }

    @Test
    void streamsRestoredDeltasFromAProviderAndEndsWithDone() throws Exception {
        MockMvc mvc = proxy(new ProxyAuthorization(List.of("proxy-token"), "sk-server", false));

        MvcResult started = mvc.perform(post("/v1/chat/completions")
                        .header("Authorization", "Bearer proxy-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"m\",\"stream\":true,\"messages\":[{\"role\":\"user\",\"content\":\"Soy juan@example.com\"}]}"))
                .andExpect(request().asyncStarted())
                .andReturn();
        started.getAsyncResult(10_000);
        String events = mvc.perform(asyncDispatch(started)).andReturn().getResponse().getContentAsString();

        assertThat(receivedAuthorization.get()).isEqualTo("Bearer sk-server");
        assertThat(receivedBody.get()).doesNotContain("juan@example.com");
        assertThat(events).contains("Tu email es ").contains("juan@example.com").doesNotContain("~REDACTED");
        assertThat(events.strip()).endsWith("data:[DONE]");
    }

    @Test
    void requestsWithoutAValidTokenAreRejected() throws Exception {
        MockMvc mvc = proxy(new ProxyAuthorization(List.of("proxy-token"), "sk-server", false));
        String body = "{\"model\":\"m\",\"messages\":[{\"role\":\"user\",\"content\":\"Hola\"}]}";

        mvc.perform(post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/chat/completions").header("Authorization", "Bearer wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        assertThat(receivedBody.get()).as("nothing reaches the provider").isNull();
    }

    @Test
    void serverKeyIsNotUsedForAnonymousRequestsByDefault() throws Exception {
        MockMvc mvc = proxy(new ProxyAuthorization(List.of(), "sk-server", false));

        mvc.perform(post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"model\":\"m\",\"messages\":[{\"role\":\"user\",\"content\":\"Hola\"}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBodiesGetABadRequest() throws Exception {
        MockMvc mvc = proxy(new ProxyAuthorization(List.of(), null, false));

        mvc.perform(post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON).content("{\"model\":\"m\"}"))
                .andExpect(status().isBadRequest());
    }

    private MockMvc proxy(ProxyAuthorization authorization) {
        ProxyService service = new ProxyService(
                new AiProviderClient("http://127.0.0.1:" + provider.getAddress().getPort()),
                new ObfuscationEngine(), true, true);
        return MockMvcBuilders.standaloneSetup(new ProxyController(service, authorization)).build();
    }

    private static String chunk(String content) {
        return "{\"id\":\"c\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\""
                + content + "\"}}]}";
    }
}
