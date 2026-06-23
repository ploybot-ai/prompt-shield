package com.ploybot.promptshield.spring.ai.advisor;

import com.ploybot.promptshield.engine.ObfuscationEngine;
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
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PromptShieldAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(PromptShieldAdvisor.class);

    public static final String OBFUSCATION_ENGINE_KEY = "prompt-shield-engine";
    public static final String CONVERSATION_ID_KEY = "prompt-shield-conversation-id";

    private static final String SYSTEM_PROMPT_EN_TEMPLATE = """
            IMPORTANT: This conversation may contain obfuscated sensitive data markers in the format %s. These are NOT errors or artifacts - they are intentional security placeholders protecting Personally Identifiable Information (PII).
            
            RULES:
            1. NEVER modify, reformat, remove, or attempt to "fix" these placeholders
            2. NEVER ask the user about them or explain what they might be
            3. Treat them as opaque identifiers - the original values will be restored after your response
            4. Reference them naturally as if they were the original data
            5. If you need to repeat the data in your response, use the EXACT placeholder as-is
            6. Do NOT try to decode, guess, or reconstruct the original values
            
            TOOL CALLS:
            When you invoke tools, use the placeholders exactly as they appear in the conversation.
            The system will automatically restore the original values before the tool is executed.
            
            Example of a tool call with obfuscated data:
            - User says: "Send email to %s"
            - You call tool: sendEmail(to="%s", subject="Hello")
            - The system restores it to: sendEmail(to="user@example.com", subject="Hello")
            
            Your task is to process the REQUEST, not the placeholders. The system will handle restoration of original values.
            """;

    private static final String SYSTEM_PROMPT_ES_TEMPLATE = """
            IMPORTANTE: Esta conversación puede contener marcadores de datos sensibles ofuscados en el formato %s. NO son errores ni artefactos - son marcadores de seguridad intencionales que protegen Información Personal Identificable (PII).
            
            REGLAS:
            1. NUNCA modifiques, reformatees, elimines o intentes "corregir" estos marcadores
            2. NUNCA preguntes al usuario sobre ellos o expliques qué podrían ser
            3. Trátalos como identificadores opacos - los valores originales se restaurarán después de tu respuesta
            4. Refiérete a ellos naturalmente como si fueran los datos originales
            5. Si necesitas repetir los datos en tu respuesta, usa el marcador EXACTO tal cual
            6. NO intentes decodificar, adivinar o reconstruir los valores originales
            
            LLAMADAS A HERRAMIENTAS:
            Cuando invoques herramientas, usa los marcadores exactamente como aparecen en la conversación.
            El sistema restaurará automáticamente los valores originales antes de ejecutar la herramienta.
            
            Ejemplo de llamada a herramienta con datos ofuscados:
            - El usuario dice: "Envía email a %s"
            - Tú llamas a la herramienta: sendEmail(to="%s", subject="Hola")
            - El sistema lo restaura a: sendEmail(to="usuario@ejemplo.com", subject="Hola")
            
            Tu tarea es procesar la SOLICITUD, no los marcadores. El sistema se encargará de restaurar los valores originales.
            """;

    private final ObfuscationEngine engine;
    private final int order;
    private final boolean restoreOnResponse;
    private final boolean injectSystemPrompt;
    private final String systemPrompt;

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
        this.engine = engine;
        this.order = order;
        this.restoreOnResponse = restoreOnResponse;
        this.injectSystemPrompt = injectSystemPrompt;
        this.systemPrompt = systemPrompt;
    }

    public static String generateSystemPrompt(ObfuscationConfig config, String language) {
        String prefix = config.getRedactedPrefix();
        String separator = config.getTagSeparator();
        String tagOpen = config.getTagOpen();
        String tagClose = config.getTagClose();
        String tagFormat = tagOpen + prefix + ":TYPE" + separator + "HASH" + tagClose;
        String exampleEmailTag = tagOpen + prefix + ":EMAIL" + separator + "e5a3b2" + tagClose;

        if ("es".equalsIgnoreCase(language)) {
            return String.format(SYSTEM_PROMPT_ES_TEMPLATE, tagFormat, exampleEmailTag, exampleEmailTag);
        }
        return String.format(SYSTEM_PROMPT_EN_TEMPLATE, tagFormat, exampleEmailTag, exampleEmailTag);
    }

    public static String generateSystemPrompt(ObfuscationConfig config) {
        return generateSystemPrompt(config, "en");
    }

    private String getEffectiveSystemPrompt() {
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            return systemPrompt;
        }
        return generateSystemPrompt(engine.getConfig());
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
        applyConversationId(chatClientRequest);
        ChatClientRequest obfuscatedRequest = obfuscateRequest(chatClientRequest);

        logUserMessage(obfuscatedRequest);
        ChatClientResponse response = callAdvisorChain.nextCall(obfuscatedRequest);
        logAssistantResponse(response);

        if (restoreOnResponse) {
            return restoreResponse(response);
        }

        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest, StreamAdvisorChain streamAdvisorChain) {
        applyConversationId(chatClientRequest);
        ChatClientRequest obfuscatedRequest = obfuscateRequest(chatClientRequest);

        logUserMessage(obfuscatedRequest);
        Flux<ChatClientResponse> responseFlux = streamAdvisorChain.nextStream(obfuscatedRequest)
                .doOnNext(this::logAssistantResponse);

        if (restoreOnResponse) {
            return responseFlux.map(this::restoreResponse);
        }

        return responseFlux;
    }

    @SuppressWarnings("unchecked")
    private void applyConversationId(ChatClientRequest request) {
        Object convId = request.context().get(CONVERSATION_ID_KEY);
        if (convId instanceof String id && !id.isBlank()) {
            engine.getConfig().setConversationId(id);
        } else {
            engine.getConfig().setConversationId(UUID.randomUUID().toString());
        }
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

    private ChatClientRequest obfuscateRequest(ChatClientRequest request) {
        Prompt prompt = request.prompt();
        List<Message> messages = new ArrayList<>();
        String obfuscationInstructions = injectSystemPrompt ? getEffectiveSystemPrompt() : null;

        for (Message message : prompt.getInstructions()) {
            if (message instanceof SystemMessage systemMessage) {
                if (injectSystemPrompt && obfuscationInstructions != null) {
                    String mergedContent = systemMessage.getText() + "\n\n" + obfuscationInstructions;
                    messages.add(new SystemMessage(mergedContent));
                    logger.debug("PromptShieldAdvisor: Merged obfuscation instructions into existing system prompt");
                } else {
                    messages.add(message);
                }
            } else if (message instanceof UserMessage userMessage) {
                String obfuscatedText = engine.ofuscar(userMessage.getText());
                messages.add(new UserMessage(obfuscatedText));
            } else if (message instanceof AssistantMessage assistantMessage) {
                String obfuscatedText = engine.ofuscar(assistantMessage.getText());
                messages.add(new AssistantMessage(obfuscatedText));
            } else {
                messages.add(message);
            }
        }

        if (injectSystemPrompt && obfuscationInstructions != null
                && messages.stream().noneMatch(m -> m instanceof SystemMessage)) {
            messages.add(0, new SystemMessage(obfuscationInstructions));
            logger.debug("PromptShieldAdvisor: Injected system prompt");
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
            AssistantMessage output = chatResponse.getResult().getOutput();
            String text = output.getText();

            boolean hasTagsInText = text != null && engine.containsTags(text);
            boolean hasToolCalls = output.hasToolCalls();

            if (hasTagsInText || hasToolCalls) {
                String restoredText = hasTagsInText ? safeRestore(text) : text;

                List<AssistantMessage.ToolCall> restoredToolCalls = null;
                if (hasToolCalls) {
                    restoredToolCalls = output.getToolCalls().stream()
                            .map(tc -> {
                                String restoredArgs = safeRestore(tc.arguments());
                                return new AssistantMessage.ToolCall(
                                        tc.id(),
                                        tc.type(),
                                        tc.name(),
                                        restoredArgs
                                );
                            })
                            .toList();
                }

                var restoredOutput = AssistantMessage.builder()
                        .content(restoredText)
                        .toolCalls(restoredToolCalls != null ? restoredToolCalls : List.of())
                        .build();
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

    private String safeRestore(String text) {
        try {
            return engine.restaurar(text);
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
}
