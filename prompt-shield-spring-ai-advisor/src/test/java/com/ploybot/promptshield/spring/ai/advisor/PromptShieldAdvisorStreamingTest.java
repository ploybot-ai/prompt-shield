package com.ploybot.promptshield.spring.ai.advisor;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptShieldAdvisorStreamingTest {

    @Mock
    private StreamAdvisorChain streamAdvisorChain;

    private ObfuscationEngine engine;
    private PromptShieldAdvisor advisor;

    @BeforeEach
    void setUp() {
        engine = new ObfuscationEngine(new ObfuscationConfig(), new InMemoryStorageService());
        advisor = new PromptShieldAdvisor(engine, 0, true, true);
    }

    private String extractTag(String obfuscated) {
        String open = engine.getConfig().getTagOpen();
        String close = engine.getConfig().getTagClose();
        int start = obfuscated.indexOf(open);
        int end = obfuscated.indexOf(close, start + open.length());
        return obfuscated.substring(start, end + close.length());
    }

    private ChatClientResponse chunk(String text) {
        ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        return ChatClientResponse.builder().chatResponse(chatResponse).build();
    }

    private String collectText(ChatClientRequest request) {
        List<ChatClientResponse> chunks = advisor.adviseStream(request, streamAdvisorChain)
                .collectList()
                .block();
        assertNotNull(chunks);
        return chunks.stream()
                .map(r -> r.chatResponse() != null && r.chatResponse().getResult() != null
                        ? r.chatResponse().getResult().getOutput().getText() : null)
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
    }

    @Test
    void tagSplitAcrossStreamChunksIsRestored() {
        UserMessage userMessage = new UserMessage("Mi email es juan@example.com");
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(List.of(userMessage)))
                .build();

        when(streamAdvisorChain.nextStream(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            ChatClientRequest req = invocation.getArgument(0);
            // index 1: the user message (the injected system prompt is at index 0)
            String obfuscated = ((UserMessage) req.prompt().getInstructions().get(1)).getText();
            assertFalse(obfuscated.contains("juan@example.com"), "request must be obfuscated");
            String tag = extractTag(obfuscated);
            int mid = tag.length() / 2;
            return Flux.just(
                    chunk("Tu email es " + tag.substring(0, mid)),
                    chunk(tag.substring(mid)),
                    chunk(" registrado"));
        });

        String full = collectText(request);

        assertEquals("Tu email es juan@example.com registrado", full);
        assertFalse(full.contains("REDACTED"), "no tag may leak into the streamed output");
    }

    @Test
    void streamEndingMidTagFlushesRemainder() {
        UserMessage userMessage = new UserMessage("Mi email es juan@example.com");
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(List.of(userMessage)))
                .build();

        when(streamAdvisorChain.nextStream(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            ChatClientRequest req = invocation.getArgument(0);
            String tag = extractTag(((UserMessage) req.prompt().getInstructions().get(1)).getText());
            String truncated = tag.substring(0, tag.length() - 1);
            return Flux.just(chunk("texto " + truncated));
        });

        String full = collectText(request);

        assertTrue(full.startsWith("texto "), "withheld text must be flushed at end of stream");
        assertTrue(full.length() > "texto ".length());
    }

    @Test
    void streamWithoutTagsPassesThrough() {
        UserMessage userMessage = new UserMessage("Hola");
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(new Prompt(List.of(userMessage)))
                .build();

        when(streamAdvisorChain.nextStream(any(ChatClientRequest.class)))
                .thenReturn(Flux.just(chunk("Hola, "), chunk("¿en qué puedo ayudarte?")));

        assertEquals("Hola, ¿en qué puedo ayudarte?", collectText(request));
    }
}
