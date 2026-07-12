package com.ploybot.promptshield.proxy.service;

import com.ploybot.promptshield.engine.ObfuscationScope;
import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.engine.StreamingRestorer;
import com.ploybot.promptshield.proxy.client.AiProviderClient;
import com.ploybot.promptshield.proxy.model.ChatCompletionRequest;
import com.ploybot.promptshield.proxy.model.ChatCompletionResponse;
import com.ploybot.promptshield.proxy.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    public ChatCompletionResponse chatCompletion(ChatCompletionRequest request, String apiKey) {
        logger.debug("ProxyService: Processing chat completion request");

        // Per-request scope: tags from one proxied request can never be restored
        // into another client's response
        ObfuscationScope scope = engine.forScope(UUID.randomUUID().toString());
        ChatCompletionRequest obfuscatedRequest = obfuscateRequest(request, scope);

        ChatCompletionResponse response = aiClient.chatCompletion(obfuscatedRequest, apiKey);

        if (restoreOnResponse) {
            return restoreResponse(response, scope);
        }

        return response;
    }

    public Flux<ChatCompletionResponse> chatCompletionStream(ChatCompletionRequest request, String apiKey) {
        logger.debug("ProxyService: Processing streaming chat completion request");

        ObfuscationScope scope = engine.forScope(UUID.randomUUID().toString());
        ChatCompletionRequest obfuscatedRequest = obfuscateRequest(request, scope);

        Flux<ChatCompletionResponse> responseFlux = aiClient.chatCompletionStream(obfuscatedRequest, apiKey);

        if (restoreOnResponse) {
            return Flux.defer(() -> {
                Map<Integer, StreamingRestorer> restorers = new HashMap<>();
                StreamState state = new StreamState();
                return responseFlux
                        .map(response -> restoreStreamResponse(response, scope, restorers, state))
                        .concatWith(Flux.defer(() -> flushStream(restorers, state)));
            });
        }

        return responseFlux;
    }

    public Object listModels(String apiKey) {
        return aiClient.listModels(apiKey);
    }

    private ChatCompletionRequest obfuscateRequest(ChatCompletionRequest request, ObfuscationScope scope) {
        List<ChatMessage> obfuscatedMessages = new ArrayList<>();
        String obfuscationInstructions = engine.generateSystemPrompt();

        for (ChatMessage message : request.messages()) {
            if ("system".equals(message.role())) {
                if (injectSystemPrompt) {
                    String mergedContent = message.content() + "\n\n" + obfuscationInstructions;
                    obfuscatedMessages.add(new ChatMessage(
                            message.role(),
                            mergedContent,
                            message.name(),
                            message.toolCalls(),
                            message.toolCallId()
                    ));
                } else {
                    obfuscatedMessages.add(message);
                }
            } else {
                String obfuscatedContent = message.content() != null
                        ? scope.obfuscate(message.content())
                        : null;
                List<com.ploybot.promptshield.proxy.model.ToolCall> obfuscatedToolCalls =
                        message.hasToolCalls()
                                ? message.toolCalls().stream()
                                .map(tc -> new com.ploybot.promptshield.proxy.model.ToolCall(
                                        tc.id(), tc.type(), tc.name(), scope.obfuscate(tc.arguments())))
                                .toList()
                                : message.toolCalls();
                obfuscatedMessages.add(new ChatMessage(
                        message.role(),
                        obfuscatedContent,
                        message.name(),
                        obfuscatedToolCalls,
                        message.toolCallId()
                ));
            }
        }

        if (injectSystemPrompt && obfuscatedMessages.stream().noneMatch(m -> "system".equals(m.role()))) {
            obfuscatedMessages.add(0, ChatMessage.system(obfuscationInstructions));
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

    private ChatCompletionResponse restoreResponse(ChatCompletionResponse response, ObfuscationScope scope) {
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
                    if (restoredContent != null && scope.containsTags(restoredContent)) {
                        restoredContent = safeRestore(restoredContent, scope);
                    }

                    List<com.ploybot.promptshield.proxy.model.ToolCall> restoredToolCalls = null;
                    if (message.hasToolCalls()) {
                        restoredToolCalls = message.toolCalls().stream()
                                .map(tc -> {
                                    String restoredArgs = safeRestore(tc.arguments(), scope);
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

    private ChatCompletionResponse restoreStreamResponse(
            ChatCompletionResponse response,
            ObfuscationScope scope,
            Map<Integer, StreamingRestorer> restorers,
            StreamState state) {
        if (response == null || response.choices() == null) {
            return response;
        }
        state.lastResponse = response;
        List<ChatCompletionResponse.Choice> choices = response.choices().stream()
                .map(choice -> {
                    state.lastChoices.put(choice.index(), choice);
                    ChatMessage message = choice.message();
                    if (message == null || message.content() == null) {
                        return restoreChoiceToolCalls(choice, scope);
                    }
                    String content = restorers
                            .computeIfAbsent(choice.index(), ignored -> scope.streamingRestorer())
                            .process(message.content());
                    ChatMessage restored = new ChatMessage(
                            message.role(), content, message.name(),
                            restoreToolCalls(message, scope), message.toolCallId());
                    return new ChatCompletionResponse.Choice(choice.index(), restored, choice.finishReason());
                })
                .toList();
        return copyResponse(response, choices);
    }

    private ChatCompletionResponse.Choice restoreChoiceToolCalls(
            ChatCompletionResponse.Choice choice, ObfuscationScope scope) {
        ChatMessage message = choice.message();
        if (message == null || !message.hasToolCalls()) {
            return choice;
        }
        ChatMessage restored = new ChatMessage(
                message.role(), message.content(), message.name(),
                restoreToolCalls(message, scope), message.toolCallId());
        return new ChatCompletionResponse.Choice(choice.index(), restored, choice.finishReason());
    }

    private List<com.ploybot.promptshield.proxy.model.ToolCall> restoreToolCalls(
            ChatMessage message, ObfuscationScope scope) {
        if (!message.hasToolCalls()) {
            return message.toolCalls();
        }
        return message.toolCalls().stream()
                .map(tc -> new com.ploybot.promptshield.proxy.model.ToolCall(
                        tc.id(), tc.type(), tc.name(), safeRestore(tc.arguments(), scope)))
                .toList();
    }

    private Flux<ChatCompletionResponse> flushStream(
            Map<Integer, StreamingRestorer> restorers, StreamState state) {
        if (state.lastResponse == null) {
            return Flux.empty();
        }
        List<ChatCompletionResponse.Choice> choices = new ArrayList<>();
        restorers.forEach((index, restorer) -> {
            if (restorer.hasPendingText()) {
                ChatCompletionResponse.Choice previous = state.lastChoices.get(index);
                ChatMessage oldMessage = previous != null ? previous.message() : null;
                ChatMessage message = new ChatMessage(
                        oldMessage != null ? oldMessage.role() : "assistant",
                        restorer.flush(),
                        oldMessage != null ? oldMessage.name() : null,
                        null,
                        oldMessage != null ? oldMessage.toolCallId() : null);
                choices.add(new ChatCompletionResponse.Choice(
                        index, message, previous != null ? previous.finishReason() : null));
            }
        });
        return choices.isEmpty() ? Flux.empty() : Flux.just(copyResponse(state.lastResponse, choices));
    }

    private ChatCompletionResponse copyResponse(
            ChatCompletionResponse response, List<ChatCompletionResponse.Choice> choices) {
        return new ChatCompletionResponse(
                response.id(), response.object(), response.created(), response.model(), choices, response.usage());
    }

    private static final class StreamState {
        private ChatCompletionResponse lastResponse;
        private final Map<Integer, ChatCompletionResponse.Choice> lastChoices = new HashMap<>();
    }

    private String safeRestore(String text, ObfuscationScope scope) {
        try {
            return scope.restore(text);
        } catch (Exception e) {
            logger.warn("ProxyService: Could not restore text: {}", e.getMessage());
            return text;
        }
    }
}
