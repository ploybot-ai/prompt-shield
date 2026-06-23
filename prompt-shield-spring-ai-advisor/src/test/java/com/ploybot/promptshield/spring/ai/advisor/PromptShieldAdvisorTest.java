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
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromptShieldAdvisorTest {

    @Mock
    private CallAdvisorChain callAdvisorChain;

    @Mock
    private StreamAdvisorChain streamAdvisorChain;

    private ObfuscationEngine engine;
    private PromptShieldAdvisor advisor;

    @BeforeEach
    void setUp() {
        engine = new ObfuscationEngine();
        advisor = new PromptShieldAdvisor(engine, 0, true, true);
    }

    @Test
    void testName() {
        assertEquals("PromptShieldAdvisor", advisor.getName());
    }

    @Test
    void testOrder() {
        assertEquals(0, advisor.getOrder());
    }

    @Test
    void testGetEngine() {
        assertSame(engine, advisor.getEngine());
    }

    @Test
    void testSystemPromptInjection() {
        // Arrange
        String originalText = "Mi DNI es 12345678Z";
        UserMessage userMessage = new UserMessage(originalText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(new AssistantMessage("Respuesta")));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            ChatClientRequest req = invocation.getArgument(0);
            List<Message> messages = req.prompt().getInstructions();
            
            // Verify system prompt was injected
            assertTrue(messages.size() >= 2);
            assertTrue(messages.get(0) instanceof SystemMessage);
            String systemText = ((SystemMessage) messages.get(0)).getText();
            assertTrue(systemText.contains(engine.getConfig().getTagOpen() + "REDACTED:TYPE" + engine.getConfig().getTagSeparator() + "HASH" + engine.getConfig().getTagClose()));
            
            // Verify user message was obfuscated
            assertTrue(messages.get(1) instanceof UserMessage);
            String userText = ((UserMessage) messages.get(1)).getText();
            assertTrue(userText.contains(engine.getConfig().getTagOpen() + "REDACTED:DNI" + engine.getConfig().getTagSeparator()));
            
            return chainResponse;
        });

        // Act
        ChatClientResponse result = advisor.adviseCall(request, callAdvisorChain);

        // Assert
        verify(callAdvisorChain, times(1)).nextCall(any(ChatClientRequest.class));
    }

    @Test
    void testNoSystemPromptWhenDisabled() {
        // Arrange
        PromptShieldAdvisor noSystemPromptAdvisor = new PromptShieldAdvisor(engine, 0, true, false);
        
        String originalText = "Mi DNI es 12345678Z";
        UserMessage userMessage = new UserMessage(originalText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(new AssistantMessage("Respuesta")));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            ChatClientRequest req = invocation.getArgument(0);
            List<Message> messages = req.prompt().getInstructions();
            
            // Verify NO system prompt was injected
            assertTrue(messages.size() == 1);
            assertTrue(messages.get(0) instanceof UserMessage);
            
            return chainResponse;
        });

        // Act
        ChatClientResponse result = noSystemPromptAdvisor.adviseCall(request, callAdvisorChain);

        // Assert
        verify(callAdvisorChain, times(1)).nextCall(any(ChatClientRequest.class));
    }

    @Test
    void testNoDuplicateSystemPrompt() {
        // Arrange - already has a system message
        SystemMessage existingSystem = new SystemMessage("Existing system prompt");
        String originalText = "Mi DNI es 12345678Z";
        UserMessage userMessage = new UserMessage(originalText);
        Prompt prompt = new Prompt(List.of(existingSystem, userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(new AssistantMessage("Respuesta")));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            ChatClientRequest req = invocation.getArgument(0);
            List<Message> messages = req.prompt().getInstructions();
            
            // Verify only one system message (merged, not duplicated)
            long systemCount = messages.stream()
                    .filter(m -> m instanceof SystemMessage)
                    .count();
            assertEquals(1, systemCount);
            
            // Verify the system message contains both original content and obfuscation instructions
            String systemText = ((SystemMessage) messages.get(0)).getText();
            assertTrue(systemText.startsWith("Existing system prompt"));
            assertTrue(systemText.contains(engine.getConfig().getTagOpen() + "REDACTED:TYPE" + engine.getConfig().getTagSeparator() + "HASH" + engine.getConfig().getTagClose()));
            
            return chainResponse;
        });

        // Act
        ChatClientResponse result = advisor.adviseCall(request, callAdvisorChain);

        // Assert
        verify(callAdvisorChain, times(1)).nextCall(any(ChatClientRequest.class));
    }

    @Test
    void testObfuscateUserMessage() {
        // Arrange
        String originalText = "Mi DNI es 12345678Z";
        UserMessage userMessage = new UserMessage(originalText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(new AssistantMessage("Respuesta")));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            ChatClientRequest req = invocation.getArgument(0);
            List<Message> messages = req.prompt().getInstructions();
            // User message is at index 1 (after system prompt)
            String text = ((UserMessage) messages.get(1)).getText();
            assertTrue(text.contains(engine.getConfig().getTagOpen() + "REDACTED:DNI" + engine.getConfig().getTagSeparator()));
            return chainResponse;
        });

        // Act
        ChatClientResponse result = advisor.adviseCall(request, callAdvisorChain);

        // Assert
        verify(callAdvisorChain, times(1)).nextCall(any(ChatClientRequest.class));
    }

    @Test
    void testObfuscateWithMultipleTypes() {
        // Arrange
        String originalText = "DNI: 12345678Z, Email: user@test.com";
        UserMessage userMessage = new UserMessage(originalText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(new AssistantMessage("Respuesta")));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            ChatClientRequest req = invocation.getArgument(0);
            List<Message> messages = req.prompt().getInstructions();
            String text = ((UserMessage) messages.get(1)).getText();
            assertTrue(text.contains(engine.getConfig().getTagOpen() + "REDACTED:DNI" + engine.getConfig().getTagSeparator()));
            assertTrue(text.contains(engine.getConfig().getTagOpen() + "REDACTED:EMAIL" + engine.getConfig().getTagSeparator()));
            return chainResponse;
        });

        // Act
        ChatClientResponse result = advisor.adviseCall(request, callAdvisorChain);

        // Assert
        verify(callAdvisorChain, times(1)).nextCall(any(ChatClientRequest.class));
    }

    @Test
    void testRestoreOnResponse() {
        // Arrange
        String originalText = "Mi DNI es 12345678Z";
        UserMessage userMessage = new UserMessage(originalText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        String responseText = "Tu DNI es {{REDACTED:DNI#1c9f96}}";
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(new AssistantMessage(responseText)));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenReturn(chainResponse);

        // Act
        ChatClientResponse result = advisor.adviseCall(request, callAdvisorChain);

        // Assert
        verify(callAdvisorChain, times(1)).nextCall(any(ChatClientRequest.class));
    }

    @Test
    void testNoRestoreOnResponse() {
        // Arrange
        PromptShieldAdvisor noRestoreAdvisor = new PromptShieldAdvisor(engine, 0, false, true);
        
        String originalText = "Mi DNI es 12345678Z";
        UserMessage userMessage = new UserMessage(originalText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        String responseText = "Tu DNI es " + engine.getConfig().getTagOpen() + "REDACTED:DNI" + engine.getConfig().getTagSeparator() + "1c9f96" + engine.getConfig().getTagClose();
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(new AssistantMessage(responseText)));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenReturn(chainResponse);

        // Act
        ChatClientResponse result = noRestoreAdvisor.adviseCall(request, callAdvisorChain);

        // Assert
        verify(callAdvisorChain, times(1)).nextCall(any(ChatClientRequest.class));
        assertNotNull(result);
        assertNotNull(result.chatResponse());
        assertNotNull(result.chatResponse().getResult());
        assertNotNull(result.chatResponse().getResult().getOutput());
        assertTrue(result.chatResponse().getResult().getOutput().getText().contains(engine.getConfig().getTagOpen() + "REDACTED:DNI"));
    }

    @Test
    void testObfuscateAssistantMessage() {
        // Arrange
        String originalText = "Tu DNI es 12345678Z";
        AssistantMessage assistantMessage = new AssistantMessage(originalText);
        Prompt prompt = new Prompt(List.of(assistantMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(new AssistantMessage("OK")));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            ChatClientRequest req = invocation.getArgument(0);
            List<Message> messages = req.prompt().getInstructions();
            // Assistant message is at index 1 (after system prompt)
            String text = ((AssistantMessage) messages.get(1)).getText();
            assertTrue(text.contains(engine.getConfig().getTagOpen() + "REDACTED:DNI" + engine.getConfig().getTagSeparator()));
            return chainResponse;
        });

        // Act
        ChatClientResponse result = advisor.adviseCall(request, callAdvisorChain);

        // Assert
        verify(callAdvisorChain, times(1)).nextCall(any(ChatClientRequest.class));
    }

    @Test
    void testCustomPrefix() {
        // Arrange
        ObfuscationConfig config = new ObfuscationConfig();
        config.setRedactedPrefix("OCULTO");
        StorageService storageService = new InMemoryStorageService();
        ObfuscationEngine customEngine = new ObfuscationEngine(config, storageService);
        PromptShieldAdvisor customAdvisor = new PromptShieldAdvisor(customEngine, 0, true, true, "Custom prompt");

        String originalText = "Mi DNI es 12345678Z";
        UserMessage userMessage = new UserMessage(originalText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(new AssistantMessage("OK")));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            ChatClientRequest req = invocation.getArgument(0);
            List<Message> messages = req.prompt().getInstructions();
            String text = ((UserMessage) messages.get(1)).getText();
            assertTrue(text.contains(customEngine.getConfig().getTagOpen() + "OCULTO:DNI" + customEngine.getConfig().getTagSeparator()));
            return chainResponse;
        });

        // Act
        ChatClientResponse result = customAdvisor.adviseCall(request, callAdvisorChain);

        // Assert
        verify(callAdvisorChain, times(1)).nextCall(any(ChatClientRequest.class));
    }

    @Test
    void testStreamObfuscate() {
        // Arrange
        String originalText = "Mi DNI es 12345678Z";
        UserMessage userMessage = new UserMessage(originalText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(new AssistantMessage("Respuesta")));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(streamAdvisorChain.nextStream(any(ChatClientRequest.class)))
                .thenReturn(Flux.just(chainResponse));

        // Act
        Flux<ChatClientResponse> resultFlux = advisor.adviseStream(request, streamAdvisorChain);

        // Assert
        StepVerifier.create(resultFlux)
                .expectNextCount(1)
                .verifyComplete();

        verify(streamAdvisorChain, times(1)).nextStream(any(ChatClientRequest.class));
    }

    @Test
    void testStreamRestoreOnResponse() {
        // Arrange
        String originalText = "Mi DNI es 12345678Z";
        UserMessage userMessage = new UserMessage(originalText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        String responseText = "Tu DNI es {{REDACTED:DNI#1c9f96}}";
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(new AssistantMessage(responseText)));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(streamAdvisorChain.nextStream(any(ChatClientRequest.class)))
                .thenReturn(Flux.just(chainResponse));

        // Act
        Flux<ChatClientResponse> resultFlux = advisor.adviseStream(request, streamAdvisorChain);

        // Assert
        StepVerifier.create(resultFlux)
                .expectNextCount(1)
                .verifyComplete();

        verify(streamAdvisorChain, times(1)).nextStream(any(ChatClientRequest.class));
    }

    @Test
    void testNoObfuscationWhenNoSensitiveData() {
        // Arrange
        String originalText = "This is a normal message without sensitive data";
        UserMessage userMessage = new UserMessage(originalText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(new AssistantMessage("OK")));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            ChatClientRequest req = invocation.getArgument(0);
            List<Message> messages = req.prompt().getInstructions();
            String text = ((UserMessage) messages.get(1)).getText();
            // Should not contain any redacted tags (only system prompt has them)
            assertFalse(text.contains("{{REDACTED:"));
            return chainResponse;
        });

        // Act
        ChatClientResponse result = advisor.adviseCall(request, callAdvisorChain);

        // Assert
        verify(callAdvisorChain, times(1)).nextCall(any(ChatClientRequest.class));
    }
}
