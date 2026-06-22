package com.ploybot.promptshield.spring.ai.advisor;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import com.ploybot.promptshield.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromptShieldAdvisorToolCallTest {

    @Mock
    private CallAdvisorChain callAdvisorChain;

    private StorageService storageService;
    private ObfuscationEngine engine;
    private PromptShieldAdvisor advisor;

    @BeforeEach
    void setUp() {
        storageService = new InMemoryStorageService();
        engine = new ObfuscationEngine(new ObfuscationConfig(), storageService);
        advisor = new PromptShieldAdvisor(engine, 0, true, true);
    }

    private String obfuscateAndGetTag(String originalValue) {
        String obfuscated = engine.ofuscar(originalValue);
        // Extract the tag from obfuscated text like "email is {{REDACTED:EMAIL#abc123}}"
        int start = obfuscated.indexOf("{{");
        int end = obfuscated.indexOf("}}") + 2;
        return obfuscated.substring(start, end);
    }

    @Test
    void testToolCallArgumentsAreRestored() {
        // Arrange - User sends email with sensitive data
        String userEmail = "Send an email to juan@example.com with subject Test";
        UserMessage userMessage = new UserMessage(userEmail);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        // First, obfuscate the email to store the tag
        String emailTag = obfuscateAndGetTag("juan@example.com");
        
        // The AI decides to call the sendEmail tool with obfuscated values
        String obfuscatedArgs = "{\"to\":\"" + emailTag + "\",\"subject\":\"Test\",\"body\":\"Hello\"}";
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call_123",
                "function",
                "sendEmail",
                obfuscatedArgs
        );
        
        AssistantMessage assistantMessage = new AssistantMessage(
                "I'll send an email for you",
                java.util.Map.of(),
                List.of(toolCall)
        );

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(assistantMessage));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenReturn(chainResponse);

        // Act
        ChatClientResponse result = advisor.adviseCall(request, callAdvisorChain);

        // Assert - Verify tool call arguments are restored
        assertNotNull(result);
        assertNotNull(result.chatResponse());
        assertNotNull(result.chatResponse().getResult());
        
        AssistantMessage output = result.chatResponse().getResult().getOutput();
        assertNotNull(output);
        assertTrue(output.hasToolCalls());
        
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
        assertEquals(1, toolCalls.size());
        
        String restoredArgs = toolCalls.get(0).arguments();
        // The email should be restored to the original value
        assertTrue(restoredArgs.contains("juan@example.com"),
                "Tool call arguments should contain restored email, but got: " + restoredArgs);
        assertFalse(restoredArgs.contains("{{REDACTED:EMAIL#"),
                "Tool call arguments should not contain obfuscated tags, but got: " + restoredArgs);
    }

    @Test
    void testToolCallWithMultipleSensitiveFields() {
        // Arrange - User sends message with DNI and email
        String userMessageText = "Send an email to juan@example.com with DNI 12345678Z";
        UserMessage userMessage = new UserMessage(userMessageText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        // Obfuscate both values to store tags
        String emailTag = obfuscateAndGetTag("juan@example.com");
        String dniTag = obfuscateAndGetTag("12345678Z");
        
        // The AI calls the tool with multiple obfuscated values
        String obfuscatedArgs = "{\"to\":\"" + emailTag + "\",\"subject\":\"DNI: " + dniTag + "\",\"body\":\"Info\"}";
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call_456",
                "function",
                "sendEmail",
                obfuscatedArgs
        );
        
        AssistantMessage assistantMessage = new AssistantMessage(
                "Sending email with DNI",
                java.util.Map.of(),
                List.of(toolCall)
        );

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(assistantMessage));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenReturn(chainResponse);

        // Act
        ChatClientResponse result = advisor.adviseCall(request, callAdvisorChain);

        // Assert - Both email and DNI should be restored
        AssistantMessage output = result.chatResponse().getResult().getOutput();
        String restoredArgs = output.getToolCalls().get(0).arguments();
        
        assertTrue(restoredArgs.contains("juan@example.com"),
                "Tool call should contain restored email, but got: " + restoredArgs);
        assertTrue(restoredArgs.contains("12345678Z"),
                "Tool call should contain restored DNI, but got: " + restoredArgs);
        assertFalse(restoredArgs.contains("{{REDACTED:"),
                "Tool call should not contain any obfuscated tags, but got: " + restoredArgs);
    }

    @Test
    void testToolCallWithoutSensitiveDataPassesThrough() {
        // Arrange - No sensitive data
        String userMessageText = "Send an email with subject Test";
        UserMessage userMessage = new UserMessage(userMessageText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        // Tool call without obfuscated values
        String cleanArgs = "{\"to\":\"test@example.com\",\"subject\":\"Test\",\"body\":\"Hello\"}";
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call_789",
                "function",
                "sendEmail",
                cleanArgs
        );
        
        AssistantMessage assistantMessage = new AssistantMessage(
                "Sending email",
                java.util.Map.of(),
                List.of(toolCall)
        );

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(assistantMessage));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenReturn(chainResponse);

        // Act
        ChatClientResponse result = advisor.adviseCall(request, callAdvisorChain);

        // Assert - Arguments should pass through unchanged
        AssistantMessage output = result.chatResponse().getResult().getOutput();
        String args = output.getToolCalls().get(0).arguments();
        
        assertTrue(args.contains("test@example.com"),
                "Tool call should contain original email");
        assertEquals(cleanArgs, args, "Tool call arguments should be unchanged");
    }

    @Test
    void testMultipleToolCallsAreAllRestored() {
        // Arrange
        String userMessageText = "Send emails to juan@example.com and maria@test.com";
        UserMessage userMessage = new UserMessage(userMessageText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        // Obfuscate both emails to store tags
        String email1Tag = obfuscateAndGetTag("juan@example.com");
        String email2Tag = obfuscateAndGetTag("maria@test.com");
        
        // Two tool calls with different obfuscated values
        String args1 = "{\"to\":\"" + email1Tag + "\",\"subject\":\"First\",\"body\":\"Hello\"}";
        String args2 = "{\"to\":\"" + email2Tag + "\",\"subject\":\"Second\",\"body\":\"World\"}";
        
        AssistantMessage.ToolCall toolCall1 = new AssistantMessage.ToolCall(
                "call_001", "function", "sendEmail", args1
        );
        AssistantMessage.ToolCall toolCall2 = new AssistantMessage.ToolCall(
                "call_002", "function", "sendEmail", args2
        );
        
        AssistantMessage assistantMessage = new AssistantMessage(
                "Sending two emails",
                java.util.Map.of(),
                List.of(toolCall1, toolCall2)
        );

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(assistantMessage));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenReturn(chainResponse);

        // Act
        ChatClientResponse result = advisor.adviseCall(request, callAdvisorChain);

        // Assert - Both tool calls should be restored
        AssistantMessage output = result.chatResponse().getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
        assertEquals(2, toolCalls.size());
        
        // Both should have restored values
        for (AssistantMessage.ToolCall tc : toolCalls) {
            assertFalse(tc.arguments().contains("{{REDACTED:"),
                    "Each tool call should have restored arguments, but got: " + tc.arguments());
        }
        
        // Verify specific values
        String restoredArgs1 = toolCalls.get(0).arguments();
        String restoredArgs2 = toolCalls.get(1).arguments();
        assertTrue(restoredArgs1.contains("juan@example.com"),
                "First tool call should have restored email");
        assertTrue(restoredArgs2.contains("maria@test.com"),
                "Second tool call should have restored email");
    }

    @Test
    void testToolCallWithSpanishSensitiveData() {
        // Arrange - Spanish format
        String userMessageText = "Envía un email a juan@example.com con su DNI";
        UserMessage userMessage = new UserMessage(userMessageText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        // Obfuscate values to store tags
        String emailTag = obfuscateAndGetTag("juan@example.com");
        String dniTag = obfuscateAndGetTag("12345678Z");
        
        // Spanish DNI format
        String obfuscatedArgs = "{\"to\":\"" + emailTag + "\",\"subject\":\"DNI: " + dniTag + "\",\"body\":\"Info\"}";
        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call_es",
                "function",
                "sendEmail",
                obfuscatedArgs
        );
        
        AssistantMessage assistantMessage = new AssistantMessage(
                "Enviando email con DNI",
                java.util.Map.of(),
                List.of(toolCall)
        );

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(assistantMessage));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenReturn(chainResponse);

        // Act
        ChatClientResponse result = advisor.adviseCall(request, callAdvisorChain);

        // Assert
        AssistantMessage output = result.chatResponse().getResult().getOutput();
        String restoredArgs = output.getToolCalls().get(0).arguments();
        
        assertTrue(restoredArgs.contains("juan@example.com"),
                "Tool call should contain restored email");
        assertTrue(restoredArgs.contains("12345678Z"),
                "Tool call should contain restored DNI");
        assertFalse(restoredArgs.contains("{{REDACTED:"),
                "Tool call should not contain obfuscated tags");
    }
}
