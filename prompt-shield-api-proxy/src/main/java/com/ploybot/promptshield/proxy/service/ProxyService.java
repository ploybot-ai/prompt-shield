package com.ploybot.promptshield.proxy.service;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.proxy.client.AiProviderClient;
import com.ploybot.promptshield.proxy.model.ChatCompletionRequest;
import com.ploybot.promptshield.proxy.model.ChatCompletionResponse;
import com.ploybot.promptshield.proxy.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProxyService {

    private static final Logger logger = LoggerFactory.getLogger(ProxyService.class);

    private final AiProviderClient aiClient;
    private final ObfuscationEngine engine;
    private final boolean injectSystemPrompt;
    private final boolean restoreOnResponse;

    public ProxyService(AiProviderClient aiClient, ObfuscationEngine engine,
                        boolean injectSystemPrompt, boolean restoreOnResponse) {
        this.aiClient = aiClient;
        this.engine = engine;
        this.injectSystemPrompt = injectSystemPrompt;
        this.restoreOnResponse = restoreOnResponse;
    }

    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request) {
        logger.debug("ProxyService: Processing chat completion request");

        ChatCompletionRequest obfuscatedRequest = obfuscateRequest(request);

        ChatCompletionResponse response = aiClient.chatCompletion(obfuscatedRequest);

        if (restoreOnResponse) {
            return restoreResponse(response);
        }

        return response;
    }

    public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest request) {
        logger.debug("ProxyService: Processing streaming chat completion request");

        ChatCompletionRequest obfuscatedRequest = obfuscateRequest(request);

        Flux<ChatCompletionResponse> responseFlux = aiClient.chatCompletionStream(obfuscatedRequest);

        if (restoreOnResponse) {
            return responseFlux.map(this::restoreResponse);
        }

        return responseFlux;
    }

    public Object listModels() {
        return aiClient.listModels();
    }

    private ChatCompletionRequest obfuscateRequest(ChatCompletionRequest request) {
        List<ChatMessage> obfuscatedMessages = new ArrayList<>();

        boolean hasSystemMessage = request.messages().stream()
                .anyMatch(m -> "system".equals(m.role()));

        if (injectSystemPrompt && !hasSystemMessage) {
            obfuscatedMessages.add(ChatMessage.system(engine.generateSystemPrompt()));
        }

        for (ChatMessage message : request.messages()) {
            if (message.content() != null && engine.containsTags(message.content()) == false) {
                String obfuscatedContent = engine.ofuscar(message.content());
                obfuscatedMessages.add(new ChatMessage(
                        message.role(),
                        obfuscatedContent,
                        message.name(),
                        message.toolCalls(),
                        message.toolCallId()
                ));
            } else if (message.hasToolCalls()) {
                List<com.ploybot.promptshield.proxy.model.ToolCall> obfuscatedToolCalls =
                        message.toolCalls().stream()
                                .map(tc -> new com.ploybot.promptshield.proxy.model.ToolCall(
                                        tc.id(),
                                        tc.type(),
                                        tc.name(),
                                        engine.ofuscar(tc.arguments())
                                ))
                                .toList();
                obfuscatedMessages.add(new ChatMessage(
                        message.role(),
                        message.content(),
                        message.name(),
                        obfuscatedToolCalls,
                        message.toolCallId()
                ));
            } else {
                obfuscatedMessages.add(message);
            }
        }

        return new ChatCompletionRequest(
                request.model(),
                obfuscatedMessages,
                request.maxTokens(),
                request.temperature(),
                request.topP(),
                request.stream(),
                request.tools(),
                request.toolChoice()
        );
    }

    private ChatCompletionResponse restoreResponse(ChatCompletionResponse response) {
        if (response == null || response.choices() == null) {
            return response;
        }

        List<ChatCompletionResponse.Choice> restoredChoices = response.choices().stream()
                .map(choice -> {
                    ChatMessage message = choice.message();
                    if (message == null) {
                        return choice;
                    }

                    String restoredContent = message.content();
                    if (restoredContent != null && engine.containsTags(restoredContent)) {
                        restoredContent = safeRestore(restoredContent);
                    }

                    List<com.ploybot.promptshield.proxy.model.ToolCall> restoredToolCalls = null;
                    if (message.hasToolCalls()) {
                        restoredToolCalls = message.toolCalls().stream()
                                .map(tc -> {
                                    String restoredArgs = safeRestore(tc.arguments());
                                    return new com.ploybot.promptshield.proxy.model.ToolCall(
                                            tc.id(),
                                            tc.type(),
                                            tc.name(),
                                            restoredArgs
                                    );
                                })
                                .toList();
                    }

                    ChatMessage restoredMessage = new ChatMessage(
                            message.role(),
                            restoredContent,
                            message.name(),
                            restoredToolCalls,
                            message.toolCallId()
                    );

                    return new ChatCompletionResponse.Choice(
                            choice.index(),
                            restoredMessage,
                            choice.finishReason()
                    );
                })
                .toList();

        return new ChatCompletionResponse(
                response.id(),
                response.object(),
                response.created(),
                response.model(),
                restoredChoices,
                response.usage()
        );
    }

    private String safeRestore(String text) {
        try {
            return engine.restaurar(text);
        } catch (Exception e) {
            logger.warn("ProxyService: Could not restore text: {}", e.getMessage());
            return text;
        }
    }
}
