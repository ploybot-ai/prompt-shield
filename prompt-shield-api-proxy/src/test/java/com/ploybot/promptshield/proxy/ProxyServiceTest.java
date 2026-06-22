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

    @Test
    void testObfuscateRequest() {
        // Arrange
        String userMessage = "Send email to juan@example.com with DNI 12345678Z";
        ChatCompletionRequest request = new ChatCompletionRequest(
                "gpt-4o-mini",
                List.of(ChatMessage.user(userMessage))
        );

        // Mock AI client to capture the request
        when(aiClient.chatCompletion(any())).thenAnswer(invocation -> {
            ChatCompletionRequest captured = invocation.getArgument(0);
            
            // Verify the request was obfuscated
            assertNotNull(captured.messages());
            // First message should be system prompt
            assertTrue(captured.messages().get(0).role().equals("system"));
            // Second message should be obfuscated user message
            String obfuscatedContent = captured.messages().get(1).content();
            assertTrue(obfuscatedContent.contains("{{REDACTED:EMAIL#"));
            assertTrue(obfuscatedContent.contains("{{REDACTED:DNI#"));
            assertFalse(obfuscatedContent.contains("juan@example.com"));
            assertFalse(obfuscatedContent.contains("12345678Z"));
            
            // Return a response
            return new ChatCompletionResponse(
                    "chatcmpl-123",
                    "chat.completion",
                    System.currentTimeMillis(),
                    "gpt-4o-mini",
                    List.of(new ChatCompletionResponse.Choice(
                            0,
                            ChatMessage.assistant("Email sent to {{REDACTED:EMAIL#abc123}}"),
                            "stop"
                    )),
                    new ChatCompletionResponse.Usage(10, 20, 30)
            );
        });

        // Act
        ChatCompletionResponse response = proxyService.chatCompletion(request);

        // Assert
        assertNotNull(response);
        verify(aiClient, times(1)).chatCompletion(any());
    }

    @Test
    void testRestoreResponse() {
        // Arrange
        ChatCompletionRequest request = new ChatCompletionRequest(
                "gpt-4o-mini",
                List.of(ChatMessage.user("Test"))
        );

        // First, obfuscate an email to store the tag
        String obfuscatedEmail = engine.ofuscar("juan@example.com");
        String emailTag = obfuscatedEmail.substring(
                obfuscatedEmail.indexOf("{{"),
                obfuscatedEmail.indexOf("}}") + 2
        );

        when(aiClient.chatCompletion(any())).thenReturn(new ChatCompletionResponse(
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

        // Act
        ChatCompletionResponse response = proxyService.chatCompletion(request);

        // Assert - Response should be restored
        assertNotNull(response);
        String content = response.choices().get(0).message().content();
        assertTrue(content.contains("juan@example.com"),
                "Response should contain restored email, but got: " + content);
        assertFalse(content.contains("{{REDACTED:"),
                "Response should not contain obfuscated tags, but got: " + content);
    }

    @Test
    void testToolCallArgumentsRestored() {
        // Arrange
        ChatCompletionRequest request = new ChatCompletionRequest(
                "gpt-4o-mini",
                List.of(ChatMessage.user("Send email"))
        );

        // Obfuscate an email to store the tag
        String obfuscatedEmail = engine.ofuscar("user@test.com");
        String emailTag = obfuscatedEmail.substring(
                obfuscatedEmail.indexOf("{{"),
                obfuscatedEmail.indexOf("}}") + 2
        );

        // AI returns a tool call with obfuscated arguments
        String toolArgs = "{\"to\":\"" + emailTag + "\",\"subject\":\"Hello\"}";
        com.ploybot.promptshield.proxy.model.ToolCall toolCall = new com.ploybot.promptshield.proxy.model.ToolCall(
                "call_123",
                "function",
                "sendEmail",
                toolArgs
        );

        when(aiClient.chatCompletion(any())).thenReturn(new ChatCompletionResponse(
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

        // Act
        ChatCompletionResponse response = proxyService.chatCompletion(request);

        // Assert - Tool call arguments should be restored
        assertNotNull(response);
        var outputMessage = response.choices().get(0).message();
        assertTrue(outputMessage.hasToolCalls());
        
        String restoredArgs = outputMessage.toolCalls().get(0).arguments();
        assertTrue(restoredArgs.contains("user@test.com"),
                "Tool call arguments should contain restored email, but got: " + restoredArgs);
        assertFalse(restoredArgs.contains("{{REDACTED:"),
                "Tool call arguments should not contain obfuscated tags, but got: " + restoredArgs);
    }

    @Test
    void testNoObfuscationWhenDisabled() {
        // Arrange - When injectSystemPrompt=false, system prompt is not injected
        // but messages are still obfuscated (for security)
        ProxyService noObfuscationService = new ProxyService(aiClient, engine, false, true);
        
        String userMessage = "Send email to juan@example.com";
        ChatCompletionRequest request = new ChatCompletionRequest(
                "gpt-4o-mini",
                List.of(ChatMessage.user(userMessage))
        );

        when(aiClient.chatCompletion(any())).thenAnswer(invocation -> {
            ChatCompletionRequest captured = invocation.getArgument(0);
            
            // Verify NO system prompt was injected (only 1 message)
            assertEquals(1, captured.messages().size());
            // Message is still obfuscated for security
            assertTrue(captured.messages().get(0).content().contains("{{REDACTED:EMAIL#"));
            
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

        // Act
        ChatCompletionResponse response = noObfuscationService.chatCompletion(request);

        // Assert
        verify(aiClient, times(1)).chatCompletion(any());
    }

    @Test
    void testNoRestoreWhenDisabled() {
        // Arrange
        ProxyService noRestoreService = new ProxyService(aiClient, engine, true, false);
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                "gpt-4o-mini",
                List.of(ChatMessage.user("Test"))
        );

        // Obfuscate an email to store the tag
        String obfuscatedEmail = engine.ofuscar("juan@example.com");
        String emailTag = obfuscatedEmail.substring(
                obfuscatedEmail.indexOf("{{"),
                obfuscatedEmail.indexOf("}}") + 2
        );

        when(aiClient.chatCompletion(any())).thenReturn(new ChatCompletionResponse(
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

        // Act
        ChatCompletionResponse response = noRestoreService.chatCompletion(request);

        // Assert - Response should NOT be restored
        assertNotNull(response);
        String content = response.choices().get(0).message().content();
        assertTrue(content.contains("{{REDACTED:"),
                "Response should contain obfuscated tags when restore is disabled");
    }

    @Test
    void testMultipleMessagesObfuscated() {
        // Arrange
        ChatCompletionRequest request = new ChatCompletionRequest(
                "gpt-4o-mini",
                List.of(
                        ChatMessage.system("You are a helpful assistant"),
                        ChatMessage.user("My DNI is 12345678Z"),
                        ChatMessage.assistant("What is your email?"),
                        ChatMessage.user("My email is test@example.com")
                )
        );

        when(aiClient.chatCompletion(any())).thenAnswer(invocation -> {
            ChatCompletionRequest captured = invocation.getArgument(0);
            
            // Verify user messages are obfuscated (system message is already present, so no new one injected)
            assertEquals(4, captured.messages().size()); // existing system + 3 messages
            assertTrue(captured.messages().get(1).content().contains("{{REDACTED:DNI#"));
            assertTrue(captured.messages().get(3).content().contains("{{REDACTED:EMAIL#"));
            
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

        // Act
        ChatCompletionResponse response = proxyService.chatCompletion(request);

        // Assert
        verify(aiClient, times(1)).chatCompletion(any());
    }
}
