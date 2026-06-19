package com.ploybot.promptshield.spring.ai.advisor;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = PromptShieldAdvisorIntegrationTest.TestConfig.class)
class PromptShieldAdvisorIntegrationTest {

    @Configuration
    @SpringBootApplication
    static class TestConfig {

        @Bean
        @Primary
        public ChatModel mockChatModel() {
            ChatModel mockModel = mock(ChatModel.class);
            
            when(mockModel.call(any(Prompt.class))).thenAnswer(invocation -> {
                Prompt prompt = invocation.getArgument(0);
                String userText = prompt.getInstructions().get(0).getText();
                
                // Return a response that includes the obfuscated data (simulating AI response)
                String responseText = "He procesado tu solicitud. Los datos que me has dado son: " + userText;
                
                AssistantMessage assistantMessage = new AssistantMessage(responseText);
                Generation generation = new Generation(assistantMessage);
                return ChatResponse.builder()
                        .generations(java.util.List.of(generation))
                        .build();
            });
            
            return mockModel;
        }
    }

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private PromptShieldAdvisor advisor;

    @Test
    void contextLoads() {
        assertNotNull(chatModel);
        assertNotNull(advisor);
    }

    @Test
    void advisorIsRegistered() {
        assertNotNull(advisor);
        assertEquals("PromptShieldAdvisor", advisor.getName());
    }

    @Test
    void obfuscationEngineIsConfigured() {
        assertNotNull(advisor.getEngine());
    }
}
