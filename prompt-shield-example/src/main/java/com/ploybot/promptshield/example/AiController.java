package com.ploybot.promptshield.example;

import com.ploybot.promptshield.spring.ai.advisor.PromptShieldAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final ChatClient chatClient;

    public AiController(ChatClient.Builder chatClientBuilder, PromptShieldAdvisor advisor) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(advisor)
                .build();
    }

    @PostMapping("/chat")
    public AiResponse chat(@RequestBody AiRequest request) {
        String response = chatClient.prompt()
                .user(request.message())
                .call()
                .content();
        return new AiResponse(response);
    }

    @PostMapping("/chat-with-context")
    public AiResponse chatWithContext(@RequestBody AiRequestWithHistory request) {
        var promptSpec = chatClient.prompt()
                .user(request.message());

        // Add conversation history if provided
        if (request.history() != null && !request.history().isEmpty()) {
            for (var msg : request.history()) {
                promptSpec.system(msg.system());
                promptSpec.user(msg.user());
            }
        }

        String response = promptSpec.call().content();
        return new AiResponse(response);
    }

    @GetMapping("/health")
    public String health() {
        return "OK - Spring AI with Prompt Shield";
    }

    public record AiRequest(String message) {}
    public record AiResponse(String response) {}
    public record AiRequestWithHistory(String message, java.util.List<ChatMessage> history) {}
    public record ChatMessage(String system, String user) {}
}
