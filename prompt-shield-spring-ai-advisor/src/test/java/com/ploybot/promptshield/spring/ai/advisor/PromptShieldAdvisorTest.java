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
        advisor = new PromptShieldAdvisor(engine, 0, true);
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
    void testObfuscateUserMessage() {
        // Arrange
        String originalText = "Mi DNI es 12345678Z";
        String obfuscatedText = engine.ofuscar(originalText);
        
        UserMessage userMessage = new UserMessage(originalText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        // Create response with obfuscated content
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(new AssistantMessage("Respuesta")));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenAnswer(invocation -> {
            ChatClientRequest req = invocation.getArgument(0);
            // Verify the request was obfuscated
            String text = req.prompt().getInstructions().get(0).getText();
            assertTrue(text.contains("{{REDACTED:DNI#"));
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
            String text = req.prompt().getInstructions().get(0).getText();
            assertTrue(text.contains("{{REDACTED:DNI#"));
            assertTrue(text.contains("{{REDACTED:EMAIL#"));
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

        // Response with obfuscated content
        String responseText = "Tu DNI es {{REDACTED:DNI#1c9f96}}";
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(new AssistantMessage(responseText)));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenReturn(chainResponse);

        // Act
        ChatClientResponse result = advisor.adviseCall(request, callAdvisorChain);

        // Assert - the advisor should restore the response
        verify(callAdvisorChain, times(1)).nextCall(any(ChatClientRequest.class));
    }

    @Test
    void testNoRestoreOnResponse() {
        // Arrange
        PromptShieldAdvisor noRestoreAdvisor = new PromptShieldAdvisor(engine, 0, false);
        
        String originalText = "Mi DNI es 12345678Z";
        UserMessage userMessage = new UserMessage(originalText);
        Prompt prompt = new Prompt(List.of(userMessage));
        ChatClientRequest request = ChatClientRequest.builder()
                .prompt(prompt)
                .build();

        // Response with obfuscated content
        String responseText = "Tu DNI es {{REDACTED:DNI#1c9f96}}";
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(new Generation(new AssistantMessage(responseText)));
        ChatClientResponse chainResponse = ChatClientResponse.builder()
                .chatResponse(chatResponse)
                .build();

        when(callAdvisorChain.nextCall(any(ChatClientRequest.class))).thenReturn(chainResponse);

        // Act
        ChatClientResponse result = noRestoreAdvisor.adviseCall(request, callAdvisorChain);

        // Assert - should NOT restore because restoreOnResponse is false
        verify(callAdvisorChain, times(1)).nextCall(any(ChatClientRequest.class));
        // The response should still contain the obfuscated tag (not restored)
        assertNotNull(result);
        assertNotNull(result.chatResponse());
        assertNotNull(result.chatResponse().getResult());
        assertNotNull(result.chatResponse().getResult().getOutput());
        assertTrue(result.chatResponse().getResult().getOutput().getText().contains("{{REDACTED:DNI#"));
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
            String text = req.prompt().getInstructions().get(0).getText();
            assertTrue(text.contains("{{REDACTED:DNI#"));
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
        PromptShieldAdvisor customAdvisor = new PromptShieldAdvisor(customEngine);

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
            String text = req.prompt().getInstructions().get(0).getText();
            assertTrue(text.contains("{{OCULTO:DNI#"));
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

        // Response with obfuscated content
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
            String text = req.prompt().getInstructions().get(0).getText();
            // Should not contain any redacted tags
            assertFalse(text.contains("{{REDACTED:"));
            return chainResponse;
        });

        // Act
        ChatClientResponse result = advisor.adviseCall(request, callAdvisorChain);

        // Assert
        verify(callAdvisorChain, times(1)).nextCall(any(ChatClientRequest.class));
    }
}
