package com.ploybot.promptshield.spring.ai.advisor;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

public class PromptShieldAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(PromptShieldAdvisor.class);

    public static final String OBFUSCATION_ENGINE_KEY = "prompt-shield-engine";

    private final ObfuscationEngine engine;
    private final int order;
    private final boolean restoreOnResponse;

    public PromptShieldAdvisor() {
        this(new ObfuscationEngine(), 0, true);
    }

    public PromptShieldAdvisor(ObfuscationEngine engine) {
        this(engine, 0, true);
    }

    public PromptShieldAdvisor(ObfuscationEngine engine, int order) {
        this(engine, order, true);
    }

    public PromptShieldAdvisor(ObfuscationEngine engine, int order, boolean restoreOnResponse) {
        this.engine = engine;
        this.order = order;
        this.restoreOnResponse = restoreOnResponse;
    }

    @Override
    public String getName() {
        return "PromptShieldAdvisor";
    }

    @Override
    public int getOrder() {
        return this.order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        logger.debug("PromptShieldAdvisor: Processing request");

        ChatClientRequest obfuscatedRequest = obfuscateRequest(chatClientRequest);

        ChatClientResponse response = callAdvisorChain.nextCall(obfuscatedRequest);

        if (restoreOnResponse) {
            return restoreResponse(response);
        }

        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        logger.debug("PromptShieldAdvisor: Processing stream request");

        ChatClientRequest obfuscatedRequest = obfuscateRequest(chatClientRequest);

        Flux<ChatClientResponse> responseFlux = streamAdvisorChain.nextStream(obfuscatedRequest);

        if (restoreOnResponse) {
            return responseFlux.map(this::restoreResponse);
        }

        return responseFlux;
    }

    private ChatClientRequest obfuscateRequest(ChatClientRequest request) {
        Prompt prompt = request.prompt();
        List<Message> messages = new ArrayList<>();

        for (Message message : prompt.getInstructions()) {
            if (message instanceof UserMessage userMessage) {
                String obfuscatedText = engine.ofuscar(userMessage.getText());
                messages.add(new UserMessage(obfuscatedText));
            } else if (message instanceof AssistantMessage assistantMessage) {
                String obfuscatedText = engine.ofuscar(assistantMessage.getText());
                messages.add(new AssistantMessage(obfuscatedText));
            } else {
                messages.add(message);
            }
        }

        Prompt obfuscatedPrompt = new Prompt(messages, prompt.getOptions());
        return request.mutate().prompt(obfuscatedPrompt).build();
    }

    private ChatClientResponse restoreResponse(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null) {
            return response;
        }

        var chatResponse = response.chatResponse();
        if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
            String text = chatResponse.getResult().getOutput().getText();
            if (engine.containsTags(text)) {
                String restoredText = engine.restaurar(text);
                var restoredOutput = new AssistantMessage(restoredText);
                var restoredResult = new org.springframework.ai.chat.model.Generation(restoredOutput);
                var restoredResponse = org.springframework.ai.chat.model.ChatResponse.builder()
                        .generations(List.of(restoredResult))
                        .build();
                return ChatClientResponse.builder()
                        .chatResponse(restoredResponse)
                        .context(response.context())
                        .build();
            }
        }

        return response;
    }

    public ObfuscationEngine getEngine() {
        return engine;
    }
}
