package com.ploybot.promptshield.spring.ai.advisor;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.engine.ObfuscationScope;
import com.ploybot.promptshield.engine.StreamingRestorer;
import com.ploybot.promptshield.engine.SystemPromptGenerator;
import com.ploybot.promptshield.model.ObfuscationConfig;
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
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Obfuscates sensitive data in every message sent to the model (system, user,
 * assistant history, tool calls and tool results) and restores it in the response.
 *
 * <p>Keep this advisor inside Spring AI's tool calling advisor (its order must be
 * greater than {@code ToolCallingAdvisor.DEFAULT_ORDER}, which the default order 0
 * is): each iteration of the tool loop then passes through it, so tools receive
 * restored arguments and their results are obfuscated before reaching the model.
 */
public class PromptShieldAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(PromptShieldAdvisor.class);

    public static final String OBFUSCATION_ENGINE_KEY = "prompt-shield-engine";
    public static final String SCOPE_ID_KEY = "prompt-shield-scope-id";

    /** @deprecated since 1.3.0; use {@link #SCOPE_ID_KEY}. */
    @Deprecated(since = "1.3.0")
    public static final String CONVERSATION_ID_KEY = "prompt-shield-conversation-id";

    private final ObfuscationEngine engine;
    private final int order;
    private final boolean restoreOnResponse;
    private final boolean injectSystemPrompt;
    private final String systemPrompt;
    private final boolean obfuscateSystemMessages;
    private final boolean obfuscateToolResponses;

    public PromptShieldAdvisor() {
        this(new ObfuscationEngine(), 0, true, true, (String) null);
    }

    public PromptShieldAdvisor(ObfuscationEngine engine) {
        this(engine, 0, true, true, (String) null);
    }

    public PromptShieldAdvisor(ObfuscationEngine engine, int order) {
        this(engine, order, true, true, (String) null);
    }

    public PromptShieldAdvisor(ObfuscationEngine engine, int order, boolean restoreOnResponse) {
        this(engine, order, restoreOnResponse, true, (String) null);
    }

    public PromptShieldAdvisor(ObfuscationEngine engine, int order, boolean restoreOnResponse, boolean injectSystemPrompt) {
        this(engine, order, restoreOnResponse, injectSystemPrompt, (String) null);
    }

    public PromptShieldAdvisor(ObfuscationEngine engine, int order, boolean restoreOnResponse, boolean injectSystemPrompt, String systemPrompt) {
        this(engine, order, restoreOnResponse, injectSystemPrompt, systemPrompt, true, true);
    }

    /**
     * @param obfuscateSystemMessages obfuscate the application's system prompt text
     * @param obfuscateToolResponses  obfuscate tool results before they reach the model
     */
    public PromptShieldAdvisor(ObfuscationEngine engine, int order, boolean restoreOnResponse, boolean injectSystemPrompt,
                               String systemPrompt, boolean obfuscateSystemMessages, boolean obfuscateToolResponses) {
        this.engine = engine;
        this.order = order;
        this.restoreOnResponse = restoreOnResponse;
        this.injectSystemPrompt = injectSystemPrompt;
        this.systemPrompt = systemPrompt;
        this.obfuscateSystemMessages = obfuscateSystemMessages;
        this.obfuscateToolResponses = obfuscateToolResponses;
    }

    public static String generateSystemPrompt(ObfuscationConfig config, String language) {
        return SystemPromptGenerator.generate(config, language);
    }

    public static String generateSystemPrompt(ObfuscationConfig config) {
        return generateSystemPrompt(config, "en");
    }

    private String getEffectiveSystemPrompt() {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            return systemPrompt;
        }
        return engine.generateSystemPrompt();
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
        ResolvedScope resolved = resolveScope(chatClientRequest);
        ObfuscationScope scope = resolved.scope();
        try {
            ChatClientRequest obfuscatedRequest = obfuscateRequest(chatClientRequest, scope);

            logUserMessage(obfuscatedRequest);
            ChatClientResponse response = callAdvisorChain.nextCall(obfuscatedRequest);
            logAssistantResponse(response);

            return restoreOnResponse ? restoreResponse(response, scope) : response;
        } finally {
            releaseIfEphemeral(resolved);
        }
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        // Everything happens per subscription: the scope, the obfuscated request and the
        // restorer state (a tag may arrive split across chunks, so text is buffered until
        // it is either a complete tag or provably not one).
        return Flux.defer(() -> {
            ResolvedScope resolved = resolveScope(chatClientRequest);
            ObfuscationScope scope = resolved.scope();
            ChatClientRequest obfuscatedRequest = obfuscateRequest(chatClientRequest, scope);

            logUserMessage(obfuscatedRequest);
            Flux<ChatClientResponse> responseFlux = streamAdvisorChain.nextStream(obfuscatedRequest)
                    .doOnNext(this::logAssistantResponse);

            if (restoreOnResponse) {
                StreamingRestorer restorer = scope.streamingRestorer();
                LastChunkHolder lastChunk = new LastChunkHolder();
                responseFlux = responseFlux
                        .map(chunk -> {
                            ChatClientResponse restored = restoreStreamChunk(chunk, restorer, scope);
                            lastChunk.value = restored;
                            return restored;
                        })
                        .concatWith(Flux.defer(() -> trailingChunk(restorer, lastChunk.value)));
            }

            return resolved.ephemeral()
                    ? responseFlux.doFinally(signal -> releaseIfEphemeral(resolved))
                    : responseFlux;
        });
    }

    private static final class LastChunkHolder {
        private volatile ChatClientResponse value;
    }

    /** A scope and whether it was generated for this request only. */
    private record ResolvedScope(ObfuscationScope scope, boolean ephemeral) {
    }

    /**
     * Resolves the isolation scope for this request. Prompt Shield's explicit scope
     * wins, followed by its legacy conversation key and Spring AI ChatMemory's
     * conversation id. If none is present, a fresh per-request scope is used and its
     * mappings are released once the response has been produced.
     */
    private ResolvedScope resolveScope(ChatClientRequest request) {
        String id = nonBlankContextValue(request, SCOPE_ID_KEY);
        if (id == null) {
            id = nonBlankContextValue(request, CONVERSATION_ID_KEY);
        }
        if (id == null) {
            id = nonBlankContextValue(request, ChatMemory.CONVERSATION_ID);
        }
        if (id == null) {
            return new ResolvedScope(engine.forScope(UUID.randomUUID().toString()), true);
        }
        return new ResolvedScope(engine.forScope(id), false);
    }

    private void releaseIfEphemeral(ResolvedScope resolved) {
        if (!resolved.ephemeral()) {
            return;
        }
        try {
            resolved.scope().removeCreatedMappings();
        } catch (RuntimeException e) {
            logger.warn("PromptShieldAdvisor: Could not release per-request mappings: {}", e.getMessage());
        }
    }

    private String nonBlankContextValue(ChatClientRequest request, String key) {
        Object value = request.context().get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private void logUserMessage(ChatClientRequest request) {
        if (!logger.isDebugEnabled()) return;
        for (Message msg : request.prompt().getInstructions()) {
            if (msg instanceof UserMessage userMessage) {
                logger.debug(">>> USER: {}", truncate(userMessage.getText()));
            }
        }
    }

    private void logAssistantResponse(ChatClientResponse response) {
        if (!logger.isDebugEnabled()) return;
        if (response == null || response.chatResponse() == null) return;
        var result = response.chatResponse().getResult();
        if (result != null && result.getOutput() != null) {
            String text = result.getOutput().getText();
            if (text != null) {
                logger.debug("<<< ASSISTANT: {}", truncate(text));
            }
        }
    }

    private String truncate(String text) {
        if (text == null) return "";
        String singleLine = text.replace("\n", " ").strip();
        return singleLine.length() <= 200 ? singleLine : singleLine.substring(0, 200) + "...";
    }

    private ChatClientRequest obfuscateRequest(ChatClientRequest request, ObfuscationScope scope) {
        Prompt prompt = request.prompt();
        List<Message> messages = new ArrayList<>();
        String obfuscationInstructions = injectSystemPrompt ? getEffectiveSystemPrompt() : null;

        for (Message message : prompt.getInstructions()) {
            if (message instanceof SystemMessage systemMessage) {
                messages.add(obfuscateSystemMessage(systemMessage, scope, obfuscationInstructions));
            } else if (message instanceof UserMessage userMessage) {
                messages.add(userMessage.mutate()
                        .text(scope.obfuscate(userMessage.getText()))
                        .build());
            } else if (message instanceof AssistantMessage assistantMessage) {
                messages.add(obfuscateAssistantMessage(assistantMessage, scope));
            } else if (message instanceof ToolResponseMessage toolResponseMessage && obfuscateToolResponses) {
                messages.add(obfuscateToolResponseMessage(toolResponseMessage, scope));
            } else {
                messages.add(message);
            }
        }

        if (obfuscationInstructions != null && messages.stream().noneMatch(m -> m instanceof SystemMessage)) {
            messages.add(0, new SystemMessage(obfuscationInstructions));
            logger.debug("PromptShieldAdvisor: Injected system prompt");
        }

        Prompt obfuscatedPrompt = new Prompt(messages, prompt.getOptions());
        return request.mutate().prompt(obfuscatedPrompt).build();
    }

    /**
     * System prompts often carry user context (name, email, account). The application
     * text is obfuscated; the obfuscation instructions are appended afterwards so their
     * example placeholder is left intact.
     */
    private SystemMessage obfuscateSystemMessage(SystemMessage message, ObfuscationScope scope, String instructions) {
        String text = obfuscateSystemMessages ? scope.obfuscate(message.getText()) : message.getText();
        if (instructions != null) {
            text = text + "\n\n" + instructions;
            logger.debug("PromptShieldAdvisor: Merged obfuscation instructions into existing system prompt");
        }
        return message.mutate().text(text).build();
    }

    private AssistantMessage obfuscateAssistantMessage(AssistantMessage message, ObfuscationScope scope) {
        List<AssistantMessage.ToolCall> toolCalls = message.getToolCalls().stream()
                .map(tc -> new AssistantMessage.ToolCall(tc.id(), tc.type(), tc.name(),
                        scope.obfuscateJsonOrText(tc.arguments())))
                .toList();
        return AssistantMessage.builder()
                .content(message.getText() != null ? scope.obfuscate(message.getText()) : null)
                .properties(message.getMetadata())
                .toolCalls(toolCalls)
                .media(message.getMedia())
                .build();
    }

    /** Tool results are a major source of personal data (customer records, emails...). */
    private ToolResponseMessage obfuscateToolResponseMessage(ToolResponseMessage message, ObfuscationScope scope) {
        List<ToolResponseMessage.ToolResponse> responses = message.getResponses().stream()
                .map(r -> new ToolResponseMessage.ToolResponse(r.id(), r.name(),
                        scope.obfuscateJsonOrText(r.responseData())))
                .toList();
        return ToolResponseMessage.builder()
                .responses(responses)
                .metadata(message.getMetadata())
                .build();
    }

    private ChatClientResponse restoreResponse(ChatClientResponse response, ObfuscationScope scope) {
        if (response == null || response.chatResponse() == null) {
            return response;
        }

        ChatResponse chatResponse = response.chatResponse();
        List<Generation> generations = chatResponse.getResults();
        if (generations == null || generations.isEmpty()) {
            Generation single = chatResponse.getResult();
            if (single == null) {
                return response;
            }
            generations = List.of(single);
        }

        boolean anyRestored = false;
        List<Generation> restoredGenerations = new ArrayList<>(generations.size());
        for (Generation generation : generations) {
            Generation restored = restoreGeneration(generation, scope);
            anyRestored |= restored != generation;
            restoredGenerations.add(restored);
        }

        if (!anyRestored) {
            return response;
        }

        ChatResponse restoredChatResponse = rebuildChatResponse(chatResponse, restoredGenerations);
        return ChatClientResponse.builder()
                .chatResponse(restoredChatResponse)
                .context(response.context())
                .build();
    }

    private Generation restoreGeneration(Generation generation, ObfuscationScope scope) {
        AssistantMessage output = generation.getOutput();
        if (output == null) {
            return generation;
        }

        String text = output.getText();
        boolean hasTagsInText = text != null && scope.containsTags(text);
        boolean hasToolCalls = output.hasToolCalls();
        if (!hasTagsInText && !hasToolCalls) {
            return generation;
        }

        String restoredText = hasTagsInText ? safeRestore(text, scope) : text;
        List<AssistantMessage.ToolCall> restoredToolCalls = output.getToolCalls().stream()
                .map(tc -> new AssistantMessage.ToolCall(tc.id(), tc.type(), tc.name(),
                        safeRestore(tc.arguments(), scope)))
                .toList();

        AssistantMessage restoredOutput = AssistantMessage.builder()
                .content(restoredText)
                .properties(output.getMetadata())
                .toolCalls(restoredToolCalls)
                .media(output.getMedia())
                .build();
        return new Generation(restoredOutput, generation.getMetadata());
    }

    private ChatClientResponse restoreStreamChunk(ChatClientResponse chunk, StreamingRestorer restorer, ObfuscationScope scope) {
        if (chunk == null || chunk.chatResponse() == null) {
            return chunk;
        }

        ChatResponse chatResponse = chunk.chatResponse();
        Generation generation = chatResponse.getResult();
        if (generation == null || generation.getOutput() == null) {
            return chunk;
        }

        AssistantMessage output = generation.getOutput();
        String text = output.getText();
        boolean hasText = text != null && !text.isEmpty();
        boolean hasToolCalls = output.hasToolCalls();
        if (!hasText && !hasToolCalls) {
            return chunk;
        }

        String emittableText = hasText ? restorer.process(text) : text;
        List<AssistantMessage.ToolCall> restoredToolCalls = output.getToolCalls().stream()
                .map(tc -> new AssistantMessage.ToolCall(tc.id(), tc.type(), tc.name(),
                        safeRestore(tc.arguments(), scope)))
                .toList();

        AssistantMessage restoredOutput = AssistantMessage.builder()
                .content(emittableText)
                .properties(output.getMetadata())
                .toolCalls(restoredToolCalls)
                .media(output.getMedia())
                .build();
        ChatResponse restoredChatResponse = rebuildChatResponse(chatResponse,
                List.of(new Generation(restoredOutput, generation.getMetadata())));
        return ChatClientResponse.builder()
                .chatResponse(restoredChatResponse)
                .context(chunk.context())
                .build();
    }

    /**
     * Emits a final synthetic chunk with any text still withheld by the restorer when
     * the stream ends (e.g. the response ended mid-tag).
     */
    private Flux<ChatClientResponse> trailingChunk(StreamingRestorer restorer, ChatClientResponse lastChunk) {
        if (!restorer.hasPendingText()) {
            return Flux.empty();
        }
        String remaining = restorer.flush();
        if (remaining.isEmpty()) {
            return Flux.empty();
        }

        AssistantMessage message = AssistantMessage.builder().content(remaining).build();
        List<Generation> generations = List.of(new Generation(message));
        ChatResponse chatResponse = (lastChunk != null && lastChunk.chatResponse() != null)
                ? rebuildChatResponse(lastChunk.chatResponse(), generations)
                : new ChatResponse(generations);
        ChatClientResponse.Builder responseBuilder = ChatClientResponse.builder().chatResponse(chatResponse);
        if (lastChunk != null) {
            responseBuilder.context(lastChunk.context());
        }
        return Flux.just(responseBuilder.build());
    }

    /**
     * Rebuilds a ChatResponse with new generations, preserving the original metadata
     * (usage, model, rate limits) when present.
     */
    private ChatResponse rebuildChatResponse(ChatResponse original, List<Generation> generations) {
        return original.getMetadata() != null
                ? new ChatResponse(generations, original.getMetadata())
                : new ChatResponse(generations);
    }

    private String safeRestore(String text, ObfuscationScope scope) {
        try {
            return scope.restore(text);
        } catch (Exception e) {
            logger.warn("PromptShieldAdvisor: Could not restore text: {}", e.getMessage());
            return text;
        }
    }

    public ObfuscationEngine getEngine() {
        return engine;
    }

    public boolean isInjectSystemPrompt() {
        return injectSystemPrompt;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public boolean isObfuscateSystemMessages() {
        return obfuscateSystemMessages;
    }

    public boolean isObfuscateToolResponses() {
        return obfuscateToolResponses;
    }
}
