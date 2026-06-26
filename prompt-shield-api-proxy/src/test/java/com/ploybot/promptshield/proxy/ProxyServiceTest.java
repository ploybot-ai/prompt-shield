package com.ploybot.promptshield.proxy;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.proxy.model.ChatCompletionRequest;
import com.ploybot.promptshield.proxy.model.ChatCompletionResponse;
import com.ploybot.promptshield.proxy.model.ChatMessage;
import com.ploybot.promptshield.proxy.service.ProxyService;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProxyServiceTest {

    @Mock
    private com.ploybot.promptshield.proxy.client.AiProviderClient aiClient;

    private ObfuscationEngine engine;
    private ProxyService proxyService;

    @BeforeEach
    void setUp() {
        ObfuscationConfig config = new ObfuscationConfig();
        engine = new ObfuscationEngine(config, new InMemoryStorageService());
        proxyService = new ProxyService(aiClient, engine, true, true);
    }

    private String extractTag(String obfuscated) {
        String open = engine.getConfig().getTagOpen();
        String close = engine.getConfig().getTagClose();
        int start = obfuscated.indexOf(open);
        int end = obfuscated.indexOf(close, start + open.length());
        if (end < 0) end = obfuscated.length();
        else end += close.length();
        return obfuscated.substring(start, end);
    }

    @Test
    void testObfuscateRequest() {
        // Arrange
        String userMessage = "Send email to juan@example.com with DNI 12345678Z";
        ChatCompletionRequest request = new ChatCompletionRequest(
                "gpt-4o-mini",
                List.of(ChatMessage.user(userMessage))
        );

        String tagOpen = engine.getConfig().getTagOpen();
        String tagSep = engine.getConfig().getTagSeparator();

        when(aiClient.chatCompletion(any(), any())).thenAnswer(invocation -> {
            ChatCompletionRequest captured = invocation.getArgument(0);
            
            assertNotNull(captured.messages());
            assertTrue(captured.messages().get(0).role().equals("system"));
            String obfuscatedContent = captured.messages().get(1).content();
            assertTrue(obfuscatedContent.contains(tagOpen + "REDACTED:EMAIL" + tagSep));
            assertTrue(obfuscatedContent.contains(tagOpen + "REDACTED:DNI" + tagSep));
            assertFalse(obfuscatedContent.contains("juan@example.com"));
            assertFalse(obfuscatedContent.contains("12345678Z"));
            
            return new ChatCompletionResponse(
                    "chatcmpl-123",
                    "chat.completion",
                    System.currentTimeMillis(),
                    "gpt-4o-mini",
                    List.of(new ChatCompletionResponse.Choice(
                            0,
                            ChatMessage.assistant("Email sent to " + extractTag(engine.ofuscar("juan@example.com"))),
                            "stop"
                    )),
                    new ChatCompletionResponse.Usage(10, 20, 30)
            );
        });

        ChatCompletionResponse response = proxyService.chatCompletion(request, null);

        assertNotNull(response);
        verify(aiClient, times(1)).chatCompletion(any(), any());
    }

    @Test
    void testRestoreResponse() {
        ChatCompletionRequest request = new ChatCompletionRequest(
                "gpt-4o-mini",
                List.of(ChatMessage.user("Test"))
        );

        String emailTag = extractTag(engine.ofuscar("juan@example.com"));

        when(aiClient.chatCompletion(any(), any())).thenReturn(new ChatCompletionResponse(
                "chatcmpl-123",
                "chat.completion",
                System.currentTimeMillis(),
                "gpt-4o-mini",
                List.of(new ChatCompletionResponse.Choice(
                        0,
                        ChatMessage.assistant("Email sent to " + emailTag),
                        "stop"
                )),
                new ChatCompletionResponse.Usage(10, 20, 30)
        ));

        ChatCompletionResponse response = proxyService.chatCompletion(request, null);

        assertNotNull(response);
        String content = response.choices().get(0).message().content();
        assertTrue(content.contains("juan@example.com"),
                "Response should contain restored email, but got: " + content);
        assertFalse(engine.containsTags(content),
                "Response should not contain obfuscated tags, but got: " + content);
    }

    @Test
    void testToolCallArgumentsRestored() {
        ChatCompletionRequest request = new ChatCompletionRequest(
                "gpt-4o-mini",
                List.of(ChatMessage.user("Send email"))
        );

        String emailTag = extractTag(engine.ofuscar("user@test.com"));

        String toolArgs = "{\"to\":\"" + emailTag + "\",\"subject\":\"Hello\"}";
        com.ploybot.promptshield.proxy.model.ToolCall toolCall = new com.ploybot.promptshield.proxy.model.ToolCall(
                "call_123",
                "function",
                "sendEmail",
                toolArgs
        );

        when(aiClient.chatCompletion(any(), any())).thenReturn(new ChatCompletionResponse(
                "chatcmpl-123",
                "chat.completion",
                System.currentTimeMillis(),
                "gpt-4o-mini",
                List.of(new ChatCompletionResponse.Choice(
                        0,
                        ChatMessage.assistant("I'll send an email", List.of(toolCall)),
                        "tool_calls"
                )),
                new ChatCompletionResponse.Usage(10, 20, 30)
        ));

        ChatCompletionResponse response = proxyService.chatCompletion(request, null);

        assertNotNull(response);
        var outputMessage = response.choices().get(0).message();
        assertTrue(outputMessage.hasToolCalls());
        
        String restoredArgs = outputMessage.toolCalls().get(0).arguments();
        assertTrue(restoredArgs.contains("user@test.com"),
                "Tool call arguments should contain restored email, but got: " + restoredArgs);
        assertFalse(engine.containsTags(restoredArgs),
                "Tool call arguments should not contain obfuscated tags, but got: " + restoredArgs);
    }

    @Test
    void testNoObfuscationWhenDisabled() {
        ProxyService noObfuscationService = new ProxyService(aiClient, engine, false, true);
        
        String userMessage = "Send email to juan@example.com";
        ChatCompletionRequest request = new ChatCompletionRequest(
                "gpt-4o-mini",
                List.of(ChatMessage.user(userMessage))
        );

        String tagOpen = engine.getConfig().getTagOpen();
        String tagSep = engine.getConfig().getTagSeparator();

        when(aiClient.chatCompletion(any(), any())).thenAnswer(invocation -> {
            ChatCompletionRequest captured = invocation.getArgument(0);
            
            assertEquals(1, captured.messages().size());
            assertTrue(captured.messages().get(0).content().contains(tagOpen + "REDACTED:EMAIL" + tagSep));
            
            return new ChatCompletionResponse(
                    "chatcmpl-123",
                    "chat.completion",
                    System.currentTimeMillis(),
                    "gpt-4o-mini",
                    List.of(new ChatCompletionResponse.Choice(
                            0,
                            ChatMessage.assistant("OK"),
                            "stop"
                    )),
                    new ChatCompletionResponse.Usage(10, 20, 30)
            );
        });

        ChatCompletionResponse response = noObfuscationService.chatCompletion(request, null);

        verify(aiClient, times(1)).chatCompletion(any(), any());
    }

    @Test
    void testNoRestoreWhenDisabled() {
        ProxyService noRestoreService = new ProxyService(aiClient, engine, true, false);
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                "gpt-4o-mini",
                List.of(ChatMessage.user("Test"))
        );

        String emailTag = extractTag(engine.ofuscar("juan@example.com"));

        when(aiClient.chatCompletion(any(), any())).thenReturn(new ChatCompletionResponse(
                "chatcmpl-123",
                "chat.completion",
                System.currentTimeMillis(),
                "gpt-4o-mini",
                List.of(new ChatCompletionResponse.Choice(
                        0,
                        ChatMessage.assistant("Email: " + emailTag),
                        "stop"
                )),
                new ChatCompletionResponse.Usage(10, 20, 30)
        ));

        ChatCompletionResponse response = noRestoreService.chatCompletion(request, null);

        assertNotNull(response);
        String content = response.choices().get(0).message().content();
        assertTrue(engine.containsTags(content),
                "Response should contain obfuscated tags when restore is disabled");
    }

    @Test
    void testMultipleMessagesObfuscated() {
        ChatCompletionRequest request = new ChatCompletionRequest(
                "gpt-4o-mini",
                List.of(
                        ChatMessage.system("You are a helpful assistant"),
                        ChatMessage.user("My DNI is 12345678Z"),
                        ChatMessage.assistant("What is your email?"),
                        ChatMessage.user("My email is test@example.com")
                )
        );

        String tagOpen = engine.getConfig().getTagOpen();
        String tagSep = engine.getConfig().getTagSeparator();

        when(aiClient.chatCompletion(any(), any())).thenAnswer(invocation -> {
            ChatCompletionRequest captured = invocation.getArgument(0);
            
            assertEquals(4, captured.messages().size());
            assertTrue(captured.messages().get(1).content().contains(tagOpen + "REDACTED:DNI" + tagSep));
            assertTrue(captured.messages().get(3).content().contains(tagOpen + "REDACTED:EMAIL" + tagSep));
            
            return new ChatCompletionResponse(
                    "chatcmpl-123",
                    "chat.completion",
                    System.currentTimeMillis(),
                    "gpt-4o-mini",
                    List.of(new ChatCompletionResponse.Choice(
                            0,
                            ChatMessage.assistant("OK"),
                            "stop"
                    )),
                    new ChatCompletionResponse.Usage(10, 20, 30)
            );
        });

        ChatCompletionResponse response = proxyService.chatCompletion(request, null);

        verify(aiClient, times(1)).chatCompletion(any(), any());
    }
}
