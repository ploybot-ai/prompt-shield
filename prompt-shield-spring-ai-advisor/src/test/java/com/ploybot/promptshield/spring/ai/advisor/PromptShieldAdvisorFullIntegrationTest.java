package com.ploybot.promptshield.spring.ai.advisor;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import com.ploybot.promptshield.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = PromptShieldAdvisorFullIntegrationTest.TestConfig.class)
class PromptShieldAdvisorFullIntegrationTest {

    @Configuration
    @SpringBootApplication
    static class TestConfig {

        @Bean
        @Primary
        public ChatModel mockChatModel() {
            ChatModel mockModel = mock(ChatModel.class);
            ChatOptions defaultOpts = ChatOptions.builder().build();
            when(mockModel.getOptions()).thenReturn(defaultOpts);
            when(mockModel.getDefaultOptions()).thenReturn(defaultOpts);

            when(mockModel.call(any(Prompt.class))).thenAnswer(invocation -> {
                Prompt prompt = invocation.getArgument(0);
                String userText = prompt.getInstructions().get(0).getText();
                
                // Return a response that simulates what an AI might return
                // (including obfuscated data that needs to be restored)
                String responseText = "He procesado tu solicitud con los datos: " + userText;
                
                AssistantMessage assistantMessage = new AssistantMessage(responseText);
                Generation generation = new Generation(assistantMessage);
                return ChatResponse.builder()
                        .generations(java.util.List.of(generation))
                        .build();
            });
            
            return mockModel;
        }

        @Bean
        public PromptShieldAdvisor promptShieldAdvisor() {
            StorageService storageService = new InMemoryStorageService();
            ObfuscationEngine engine = new ObfuscationEngine(new com.ploybot.promptshield.model.ObfuscationConfig(), storageService);
            return new PromptShieldAdvisor(engine, 0, true, true);
        }
    }

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private PromptShieldAdvisor advisor;

    @Test
    void testFullFlowWithChatClient() {
        // Create a ChatClient with the advisor
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(advisor)
                .build();

        // Send a message with sensitive data
        String userMessage = "Mi DNI es 12345678Z y mi email es user@test.com";
        
        String response = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        // Verify we got a response
        assertNotNull(response);
        assertFalse(response.isEmpty());
    }

    @Test
    void testMultipleMessages() {
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(advisor)
                .build();

        // First message
        String response1 = chatClient.prompt()
                .user("Mi DNI es 12345678Z")
                .call()
                .content();

        assertNotNull(response1);

        // Second message
        String response2 = chatClient.prompt()
                .user("Mi email es user@test.com")
                .call()
                .content();

        assertNotNull(response2);
    }

    @Test
    void testWithMixedSensitiveData() {
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(advisor)
                .build();

        String userMessage = "Nombre: Juan García, DNI: 12345678Z, Email: juan@test.com, Teléfono: 612345678";
        
        String response = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        assertNotNull(response);
    }

    @Test
    void testWithoutSensitiveData() {
        ChatClient chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(advisor)
                .build();

        String userMessage = "This is a normal message without any sensitive data";
        
        String response = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();

        assertNotNull(response);
    }
}
