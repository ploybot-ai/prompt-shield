package com.ploybot.promptshield.proxy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.engine.ObfuscationScope;
import com.ploybot.promptshield.engine.StreamingRestorer;
import com.ploybot.promptshield.injection.ContentSource;
import com.ploybot.promptshield.injection.InjectionAction;
import com.ploybot.promptshield.injection.InjectionGuard;
import com.ploybot.promptshield.injection.InjectionVerdict;
import com.ploybot.promptshield.injection.PromptInjectionException;
import com.ploybot.promptshield.proxy.client.AiProviderClient;
import com.ploybot.promptshield.proxy.client.AiProviderClient.ProviderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Obfuscates OpenAI-compatible chat completion requests and restores the responses.
 * It works on the raw JSON, so every field it does not touch reaches the provider and
 * the client unchanged. Each request gets its own scope, released once the response
 * has been restored.
 *
 * <p>With an {@link InjectionGuard}, user messages, tool messages and tool definitions
 * are scanned for prompt injection first; see {@link InjectionGuard} for the actions and
 * for why a flagged tool result keeps placeholders in the response's tool calls and URLs.
 */
public class ProxyService {

    private static final Logger logger = LoggerFactory.getLogger(ProxyService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiProviderClient aiClient;
    private final ObfuscationEngine engine;
    private final boolean injectSystemPrompt;
    private final boolean restoreOnResponse;
    private final InjectionGuard injectionGuard;
    private final boolean protectOnInjection;

    public ProxyService(AiProviderClient aiClient, ObfuscationEngine engine,
                        boolean injectSystemPrompt, boolean restoreOnResponse) {
        this(aiClient, engine, injectSystemPrompt, restoreOnResponse, null, true);
    }

    /**
     * @param injectionGuard     prompt-injection detection, or {@code null} to disable it
     * @param protectOnInjection keep placeholders in tool calls and URLs of the response
     *                           when a tool message was flagged but let through
     */
    public ProxyService(AiProviderClient aiClient, ObfuscationEngine engine, boolean injectSystemPrompt,
                        boolean restoreOnResponse, InjectionGuard injectionGuard, boolean protectOnInjection) {
        this.aiClient = aiClient;
        this.engine = engine;
        this.injectSystemPrompt = injectSystemPrompt;
        this.restoreOnResponse = restoreOnResponse;
        this.injectionGuard = injectionGuard;
        this.protectOnInjection = protectOnInjection;
    }

    /** Thrown when the client request is not a JSON object with a {@code messages} array. */
    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }

    public static boolean isStreamRequest(String requestJson) {
        return parseRequest(requestJson).path("stream").asBoolean(false);
    }

    public ProviderResponse chatCompletion(String requestJson, String authorization) {
        ObfuscationScope scope = newRequestScope();
        try {
            ObjectNode parsed = parseRequest(requestJson);
            boolean protect = inspect(parsed);
            ObjectNode request = obfuscateRequest(parsed, scope);
            ProviderResponse response = aiClient.chatCompletion(write(request), authorization);
            if (!restoreOnResponse || !response.isSuccess()) {
                return response;
            }
            return new ProviderResponse(response.status(), restoreResponse(response.body(), scope, protect));
        } finally {
            scope.removeCreatedMappings();
        }
    }

    /**
     * Returns the {@code data} payload of each streamed chunk, restored. The caller adds
     * the terminating {@code [DONE]} event.
     */
    public Flux<String> chatCompletionStream(String requestJson, String authorization) {
        // Parsed and inspected eagerly: a blocked request fails before the event stream opens
        ObjectNode parsed = parseRequest(requestJson);
        boolean protect = inspect(parsed);
        return Flux.defer(() -> {
            ObfuscationScope scope = newRequestScope();
            ObjectNode request = obfuscateRequest(parsed, scope);
            Flux<String> upstream = aiClient.chatCompletionStream(write(request), authorization);
            if (!restoreOnResponse) {
                return upstream.doFinally(signal -> scope.removeCreatedMappings());
            }
            StreamState state = new StreamState(scope, protect);
            return upstream
                    .map(chunk -> restoreStreamChunk(chunk, state))
                    .concatWith(Flux.defer(() -> Flux.fromIterable(state.trailingChunks())))
                    .doFinally(signal -> scope.removeCreatedMappings());
        });
    }

    public ProviderResponse listModels(String authorization) {
        return aiClient.listModels(authorization);
    }

    /**
     * Scans the request in place: quarantined tool messages are replaced, blocked
     * requests throw {@link PromptInjectionException}.
     *
     * @return whether the response must keep placeholders in tool calls and URLs
     */
    private boolean inspect(ObjectNode request) {
        if (injectionGuard == null) {
            return false;
        }
        InjectionGuard.Inspection inspection = injectionGuard.newInspection();
        List<InjectionVerdict> blockedDefinitions = new java.util.ArrayList<>();
        if (request.get("tools") instanceof ArrayNode tools) {
            for (JsonNode tool : tools) {
                JsonNode function = tool.path("function");
                String text = function.path("name").asText("") + "\n" + function.path("description").asText("")
                        + "\n" + function.path("parameters").toString();
                InjectionGuard.Decision decision = inspection.inspect(text, ContentSource.TOOL_DEFINITION);
                report(decision);
                if (decision.action() == InjectionAction.QUARANTINE) {
                    blockedDefinitions.add(decision.verdict());
                }
            }
        }
        for (JsonNode node : (ArrayNode) request.get("messages")) {
            if (!(node instanceof ObjectNode message)) {
                continue;
            }
            String role = message.path("role").asText();
            ContentSource source = switch (role) {
                case "user" -> ContentSource.USER;
                case "tool", "function" -> ContentSource.TOOL_RESULT;
                default -> null;
            };
            if (source != null) {
                transformContent(message, text -> {
                    InjectionGuard.Decision decision = inspection.inspect(text, source);
                    report(decision);
                    return decision.text();
                });
            }
        }
        if (!blockedDefinitions.isEmpty()) {
            throw new PromptInjectionException(blockedDefinitions);
        }
        inspection.throwIfBlocked();
        boolean protect = protectOnInjection && inspection.isTainted();
        if (protect) {
            logger.warn("ProxyService: a tool message was flagged; placeholders in tool calls and URLs of the "
                    + "response are not restored");
        }
        return protect;
    }

    private static void report(InjectionGuard.Decision decision) {
        if (decision.flagged()) {
            logger.warn("ProxyService: possible prompt injection ({}): {}", decision.action(), decision.verdict().summary());
        }
    }

    private ObfuscationScope newRequestScope() {
        return engine.forScope("proxy-" + UUID.randomUUID());
    }

    private static ObjectNode parseRequest(String requestJson) {
        try {
            JsonNode node = MAPPER.readTree(requestJson);
            if (node instanceof ObjectNode object && object.get("messages") instanceof ArrayNode) {
                return object;
            }
        } catch (JsonProcessingException | IllegalArgumentException e) {
            // reported below
        }
        throw new InvalidRequestException("Request body must be a JSON object with a messages array");
    }

    private ObjectNode obfuscateRequest(ObjectNode request, ObfuscationScope scope) {
        ArrayNode messages = (ArrayNode) request.get("messages");
        String instructions = injectSystemPrompt ? engine.generateSystemPrompt() : null;
        boolean instructionsMerged = false;

        for (JsonNode node : messages) {
            if (!(node instanceof ObjectNode message)) {
                continue;
            }
            transformContent(message, scope::obfuscate);
            transformToolCallArguments(message, scope::obfuscateJsonOrText);

            String role = message.path("role").asText();
            if (instructions != null && !instructionsMerged && ("system".equals(role) || "developer".equals(role))) {
                appendText(message, instructions);
                instructionsMerged = true;
            }
        }

        if (instructions != null && !instructionsMerged) {
            ObjectNode system = MAPPER.createObjectNode().put("role", "system").put("content", instructions);
            messages.insert(0, system);
        }
        return request;
    }

    /** Transforms string content, or the text parts of multimodal content. */
    private static void transformContent(ObjectNode message, UnaryOperator<String> transform) {
        JsonNode content = message.get("content");
        if (content != null && content.isTextual()) {
            message.put("content", transform.apply(content.asText()));
        } else if (content instanceof ArrayNode parts) {
            for (JsonNode part : parts) {
                if (part instanceof ObjectNode partObject && part.path("text").isTextual()) {
                    partObject.put("text", transform.apply(part.get("text").asText()));
                }
            }
        }
    }

    private static void transformToolCallArguments(ObjectNode message, UnaryOperator<String> transform) {
        JsonNode toolCalls = message.get("tool_calls");
        if (toolCalls instanceof ArrayNode calls) {
            for (JsonNode call : calls) {
                transformArguments(call.get("function"), transform);
            }
        }
        transformArguments(message.get("function_call"), transform);
    }

    private static void transformArguments(JsonNode function, UnaryOperator<String> transform) {
        if (function instanceof ObjectNode functionObject && function.path("arguments").isTextual()) {
            functionObject.put("arguments", transform.apply(function.get("arguments").asText()));
        }
    }

    private static void appendText(ObjectNode message, String text) {
        JsonNode content = message.get("content");
        if (content instanceof ArrayNode parts) {
            parts.addObject().put("type", "text").put("text", text);
        } else {
            String existing = content != null && content.isTextual() ? content.asText() : "";
            message.put("content", existing.isEmpty() ? text : existing + "\n\n" + text);
        }
    }

    private String restoreResponse(String body, ObfuscationScope scope, boolean protect) {
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            logger.warn("ProxyService: Provider response is not JSON; returned unchanged");
            return body;
        }
        for (JsonNode choice : root.path("choices")) {
            if (choice.get("message") instanceof ObjectNode message) {
                transformContent(message, protect ? scope::restoreOutsideUrls : scope::restore);
                if (!protect) {
                    transformToolCallArguments(message, scope::restore);
                }
            }
        }
        return write(root);
    }

    private String restoreStreamChunk(String chunk, StreamState state) {
        JsonNode root;
        try {
            root = MAPPER.readTree(chunk);
        } catch (JsonProcessingException e) {
            return chunk;
        }
        if (!(root instanceof ObjectNode rootObject)) {
            return chunk;
        }
        state.lastChunk = rootObject;

        for (JsonNode choiceNode : root.path("choices")) {
            if (!(choiceNode instanceof ObjectNode choice)) {
                continue;
            }
            int choiceIndex = choice.path("index").asInt(0);
            boolean finished = choice.hasNonNull("finish_reason");
            ObjectNode delta = choice.get("delta") instanceof ObjectNode existing ? existing : null;

            if (delta != null && delta.path("content").isTextual()) {
                delta.put("content", state.restorer(choiceIndex, -1).process(delta.get("content").asText()));
            }
            if (delta != null && delta.get("tool_calls") instanceof ArrayNode calls) {
                for (JsonNode call : calls) {
                    if (!state.protect && call.get("function") instanceof ObjectNode function
                            && function.path("arguments").isTextual()) {
                        int callIndex = call.path("index").asInt(0);
                        function.put("arguments", state.restorer(choiceIndex, callIndex)
                                .process(function.get("arguments").asText()));
                    }
                }
            }
            if (finished) {
                // Nothing can follow for this choice: release what was held back
                state.flushInto(choice, choiceIndex);
            }
        }
        return write(root);
    }

    private static String write(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize JSON", e);
        }
    }

    /** Restorers for one stream, per choice and per tool call (-1 is the text content). */
    private static final class StreamState {
        private final ObfuscationScope scope;
        private final boolean protect;
        private final Map<String, StreamingRestorer> restorers = new LinkedHashMap<>();
        private ObjectNode lastChunk;

        StreamState(ObfuscationScope scope, boolean protect) {
            this.scope = scope;
            this.protect = protect;
        }

        StreamingRestorer restorer(int choiceIndex, int callIndex) {
            return restorers.computeIfAbsent(choiceIndex + ":" + callIndex,
                    key -> protect ? scope.streamingRestorerOutsideUrls() : scope.streamingRestorer());
        }

        void flushInto(ObjectNode choice, int choiceIndex) {
            ObjectNode delta = choice.get("delta") instanceof ObjectNode existing ? existing : choice.putObject("delta");
            restorers.forEach((key, restorer) -> {
                String[] parts = key.split(":");
                if (Integer.parseInt(parts[0]) != choiceIndex || !restorer.hasPendingText()) {
                    return;
                }
                String pending = restorer.flush();
                int callIndex = Integer.parseInt(parts[1]);
                if (callIndex < 0) {
                    String content = delta.path("content").isTextual() ? delta.get("content").asText() : "";
                    delta.put("content", content + pending);
                } else {
                    ArrayNode calls = delta.get("tool_calls") instanceof ArrayNode existing ? existing : delta.putArray("tool_calls");
                    calls.addObject().put("index", callIndex).putObject("function").put("arguments", pending);
                }
            });
        }

        /** A final chunk for text still held back when the stream ended without finish_reason. */
        Iterable<String> trailingChunks() {
            if (lastChunk == null || restorers.values().stream().noneMatch(StreamingRestorer::hasPendingText)) {
                return List.of();
            }
            ObjectNode chunk = lastChunk.deepCopy();
            chunk.remove("usage");
            ArrayNode choices = chunk.putArray("choices");
            restorers.keySet().stream()
                    .map(key -> Integer.parseInt(key.split(":")[0]))
                    .distinct()
                    .forEach(choiceIndex -> {
                        ObjectNode choice = MAPPER.createObjectNode().put("index", choiceIndex);
                        choice.putObject("delta");
                        choice.putNull("finish_reason");
                        flushInto(choice, choiceIndex);
                        if (!choice.get("delta").isEmpty()) {
                            choices.add(choice);
                        }
                    });
            return choices.isEmpty() ? List.of() : List.of(write(chunk));
        }
    }
}
