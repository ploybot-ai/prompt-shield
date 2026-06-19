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
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

public class PromptShieldAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger logger = LoggerFactory.getLogger(PromptShieldAdvisor.class);

    public static final String OBFUSCATION_ENGINE_KEY = "prompt-shield-engine";

    public static final String SYSTEM_PROMPT_EN = """
            IMPORTANT: This conversation may contain obfuscated sensitive data markers in the format {{REDACTED:TYPE#HASH}}. These are NOT errors or artifacts - they are intentional security placeholders protecting Personally Identifiable Information (PII).
            
            RULES:
            1. NEVER modify, reformat, remove, or attempt to "fix" these placeholders
            2. NEVER ask the user about them or explain what they might be
            3. Treat them as opaque identifiers - the original values will be restored after your response
            4. Reference them naturally as if they were the original data
            5. If you need to repeat the data in your response, use the EXACT placeholder as-is
            6. Do NOT try to decode, guess, or reconstruct the original values
            
            Your task is to process the REQUEST, not the placeholders. The system will handle restoration of original values.
            """;

    public static final String SYSTEM_PROMPT_ES = """
            IMPORTANTE: Esta conversación puede contener marcadores de datos sensibles ofuscados en el formato {{REDACTED:TYPE#HASH}}. NO son errores ni artefactos - son marcadores de seguridad intencionales que protegen Información Personal Identificable (PII).
            
            REGLAS:
            1. NUNCA modifiques, reformatees, elimines o intentes "corregir" estos marcadores
            2. NUNCA preguntes al usuario sobre ellos o expliques qué podrían ser
            3. Trátalos como identificadores opacos - los valores originales se restaurarán después de tu respuesta
            4. Refiérete a ellos naturalmente como si fueran los datos originales
            5. Si necesitas repetir los datos en tu respuesta, usa el marcador EXACTO tal cual
            6. NO intentes decodificar, adivinar o reconstruir los valores originales
            
            Tu tarea es procesar la SOLICITUD, no los marcadores. El sistema se encargará de restaurar los valores originales.
            """;

    private final ObfuscationEngine engine;
    private final int order;
    private final boolean restoreOnResponse;
    private final boolean injectSystemPrompt;
    private final String systemPrompt;

    public PromptShieldAdvisor() {
        this(new ObfuscationEngine(), 0, true, true, SYSTEM_PROMPT_EN);
    }

    public PromptShieldAdvisor(ObfuscationEngine engine) {
        this(engine, 0, true, true, SYSTEM_PROMPT_EN);
    }

    public PromptShieldAdvisor(ObfuscationEngine engine, int order) {
        this(engine, order, true, true, SYSTEM_PROMPT_EN);
    }

    public PromptShieldAdvisor(ObfuscationEngine engine, int order, boolean restoreOnResponse) {
        this(engine, order, restoreOnResponse, true, SYSTEM_PROMPT_EN);
    }

    public PromptShieldAdvisor(ObfuscationEngine engine, int order, boolean restoreOnResponse, boolean injectSystemPrompt) {
        this(engine, order, restoreOnResponse, injectSystemPrompt, SYSTEM_PROMPT_EN);
    }

    public PromptShieldAdvisor(ObfuscationEngine engine, int order, boolean restoreOnResponse, boolean injectSystemPrompt, String systemPrompt) {
        this.engine = engine;
        this.order = order;
        this.restoreOnResponse = restoreOnResponse;
        this.injectSystemPrompt = injectSystemPrompt;
        this.systemPrompt = systemPrompt;
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
        boolean hasSystemMessage = false;

        // Check if there's already a system message
        for (Message message : prompt.getInstructions()) {
            if (message instanceof SystemMessage) {
                hasSystemMessage = true;
                break;
            }
        }

        // Add system prompt if configured and not already present
        if (injectSystemPrompt && !hasSystemMessage && systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
            logger.debug("PromptShieldAdvisor: Injected system prompt");
        }

        // Obfuscate messages
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

    public boolean isInjectSystemPrompt() {
        return injectSystemPrompt;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }
}
