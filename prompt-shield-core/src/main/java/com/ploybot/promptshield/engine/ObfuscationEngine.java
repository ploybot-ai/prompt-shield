package com.ploybot.promptshield.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ploybot.promptshield.exception.ObfuscationException;
import com.ploybot.promptshield.hash.HashGenerator;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.model.ObfuscationTag;
import com.ploybot.promptshield.registry.DataTypeRegistry;
import com.ploybot.promptshield.registry.SensitiveDataType;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import com.ploybot.promptshield.storage.StorageService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ObfuscationEngine {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /** Upper bound for collision-extended tag identifiers, in hex characters. */
    private static final int MAX_ID_HEX_LENGTH = 64;

    /** Unambiguous separator between the scope id and value in the HMAC input. */
    private static final char HMAC_INPUT_SEPARATOR = '\0';

    private final HashGenerator hashGenerator;
    private final DataTypeRegistry registry;
    private final StorageService storageService;
    private final ObfuscationConfig config;
    private final Pattern tagPattern;

    public ObfuscationEngine(ObfuscationConfig config, StorageService storageService) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (storageService == null) {
            throw new IllegalArgumentException("storageService must not be null");
        }
        if (config.getHashLength() < 1 || config.getHashLength() > MAX_ID_HEX_LENGTH) {
            throw new IllegalArgumentException("hashLength must be between 1 and " + MAX_ID_HEX_LENGTH);
        }
        this.config = config;
        this.hashGenerator = new HashGenerator(config.getHashAlgorithm(), config.getHashLength(), config.getHmacSecret());
        this.registry = new DataTypeRegistry(config.isServiceKeysEnabled(), config.isChecksumValidationEnabled());
        this.storageService = storageService;
        this.tagPattern = buildTagPattern();

        for (Map.Entry<String, ObfuscationConfig.CustomTypeConfig> entry : config.getCustomTypes().entrySet()) {
            registry.register(entry.getKey(), entry.getValue().getPattern());
        }
    }

    public ObfuscationEngine() {
        this(new ObfuscationConfig(), new InMemoryStorageService());
    }

    public ObfuscationEngine(ObfuscationConfig config) {
        this(config, new InMemoryStorageService());
    }

    /**
     * Returns a view of this engine bound to an opaque isolation scope. A scope may
     * represent a conversation, user, tenant, request, or any other boundary chosen
     * by the caller. Scopes can be used concurrently on the same engine.
     */
    public ObfuscationScope forScope(String scopeId) {
        if (scopeId == null || scopeId.isBlank()) {
            throw new IllegalArgumentException("scopeId must not be null or blank");
        }
        return new ObfuscationScope(this, scopeId);
    }

    private String scopedKey(String scopeId, String id) {
        return scopeId + ":" + id;
    }

    private Pattern buildTagPattern() {
        String prefix = Pattern.quote(config.getTagOpen() + config.getRedactedPrefix() + ":");
        String separator = Pattern.quote(config.getTagSeparator());
        String suffix = Pattern.quote(config.getTagClose());
        // {n,MAX}: identifiers are extended beyond hashLength when a truncated prefix collides
        return Pattern.compile(prefix + "([A-Z_]+)" + separator
                + "([a-f0-9]{" + config.getHashLength() + "," + MAX_ID_HEX_LENGTH + "})" + suffix);
    }

    public Pattern getTagPattern() {
        return tagPattern;
    }

    @SuppressWarnings("deprecation")
    private String defaultScopeId() {
        return config.getScopeId();
    }

    public String ofuscar(String text) {
        return obfuscateScoped(text, defaultScopeId());
    }

    public String ofuscar(String text, String typeName) {
        return obfuscateScoped(text, typeName, defaultScopeId());
    }

    public String ofuscar(String text, SensitiveDataType type) {
        return ofuscar(text, type.getTypeName());
    }

    public String restaurar(String text) {
        return restoreScoped(text, defaultScopeId());
    }

    String obfuscateScoped(String text, String scopeId) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;
        List<String> processedTypes = new ArrayList<>(registry.getRegisteredTypeNames());

        for (String typeName : processedTypes) {
            Pattern pattern = registry.getPattern(typeName);
            if (pattern != null) {
                result = ofuscarTipo(result, typeName, pattern, scopeId);
            }
        }

        return result;
    }

    String obfuscateScoped(String text, String typeName, String scopeId) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Pattern pattern = registry.getPattern(typeName);
        if (pattern == null) {
            throw new ObfuscationException("Type not registered: " + typeName);
        }

        return ofuscarTipo(text, typeName, pattern, scopeId);
    }

    String obfuscateValueScoped(String value, String typeName, String scopeId) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (typeName == null || !typeName.matches("[A-Z_]+")) {
            throw new IllegalArgumentException("typeName must match [A-Z_]+");
        }
        String id = assignId(typeName, value, scopeId);
        return formatTag(typeName, id);
    }

    private String ofuscarTipo(String text, String typeName, Pattern pattern, String scopeId) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        Predicate<String> validator = registry.getValidator(typeName);

        while (matcher.find()) {
            String matchedValue = matcher.group();
            if (validator != null && !validator.test(matchedValue)) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matchedValue));
                continue;
            }
            String id = assignId(typeName, matchedValue, scopeId);
            String tag = formatTag(typeName, id);
            matcher.appendReplacement(result, Matcher.quoteReplacement(tag));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Assigns a stable identifier to the value within the scope. The id is a
     * truncated keyed HMAC; on the (rare) prefix collision with a different value it
     * is deterministically extended one hex character at a time until it is unique.
     */
    private String assignId(String typeName, String value, String scopeId) {
        String fullHash = hashGenerator.generateFull(scopeId + HMAC_INPUT_SEPARATOR + value);
        int maxLength = Math.min(fullHash.length(), MAX_ID_HEX_LENGTH);
        String id = fullHash.substring(0, Math.min(hashGenerator.getHashLength(), maxLength));

        // StorageService predates atomic put-if-absent operations. Serializing the
        // read/store pair on the shared service prevents in-process collision races.
        synchronized (storageService) {
            while (true) {
                String key = scopedKey(scopeId, id);
                Optional<ObfuscationTag> existing = storageService.retrieve(key);
                if (existing.isEmpty()) {
                    storageService.store(key, new ObfuscationTag(typeName, id, value));
                    return id;
                }
                if (existing.get().getOriginalValue().equals(value)) {
                    return id;
                }
                if (id.length() >= maxLength) {
                    throw new ObfuscationException("Unable to assign a unique tag id for type " + typeName);
                }
                id = fullHash.substring(0, id.length() + 1);
            }
        }
    }

    private String formatTag(String typeName, String id) {
        return config.getTagOpen() + config.getRedactedPrefix() + ":" + typeName + config.getTagSeparator() + id + config.getTagClose();
    }

    String restoreScoped(String text, String scopeId) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = tagPattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String id = matcher.group(2);
            String key = scopedKey(scopeId, id);

            Optional<ObfuscationTag> tagOpt = storageService.retrieve(key);
            if (tagOpt.isPresent()) {
                String originalValue = tagOpt.get().getOriginalValue();
                matcher.appendReplacement(result, Matcher.quoteReplacement(originalValue));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            }
        }

        matcher.appendTail(result);
        return result.toString();
    }

    public boolean containsTags(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return tagPattern.matcher(text).find();
    }

    public List<ObfuscationTag> extractTags(String text) {
        return extractTagsScoped(text, defaultScopeId());
    }

    List<ObfuscationTag> extractTagsScoped(String text, String scopeId) {
        List<ObfuscationTag> tags = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tags;
        }

        Matcher matcher = tagPattern.matcher(text);
        while (matcher.find()) {
            String id = matcher.group(2);
            String key = scopedKey(scopeId, id);
            storageService.retrieve(key).ifPresent(tags::add);
        }

        return tags;
    }

    int removeMappingsScoped(String text, String scopeId) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int removed = 0;
        Matcher matcher = tagPattern.matcher(text);
        while (matcher.find()) {
            String key = scopedKey(scopeId, matcher.group(2));
            if (storageService.contains(key)) {
                storageService.remove(key);
                removed++;
            }
        }
        return removed;
    }

    public void clearStorage() {
        storageService.clear();
    }

    public HashGenerator getHashGenerator() {
        return hashGenerator;
    }

    public DataTypeRegistry getRegistry() {
        return registry;
    }

    public StorageService getStorageService() {
        return storageService;
    }

    public ObfuscationConfig getConfig() {
        return config;
    }

    public String generateSystemPrompt() {
        return generateSystemPrompt("en");
    }

    public String generateSystemPrompt(String language) {
        String prefix = config.getRedactedPrefix();
        String separator = config.getTagSeparator();
        String tagOpen = config.getTagOpen();
        String tagClose = config.getTagClose();
        String tagFormat = tagOpen + prefix + ":TYPE" + separator + "HASH" + tagClose;
        String exampleEmailTag = tagOpen + prefix + ":EMAIL" + separator + "e5a3b2" + tagClose;

        String template;
        if ("es".equalsIgnoreCase(language)) {
            template = """
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
        } else {
            template = """
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
        }

        return String.format(template, tagFormat, exampleEmailTag, exampleEmailTag);
    }

    public String ofuscarObjeto(Object objeto) {
        if (objeto == null) {
            return null;
        }

        try {
            String json = JSON_MAPPER.writeValueAsString(objeto);
            return ofuscar(json);
        } catch (JsonProcessingException e) {
            throw new ObfuscationException("Error serializing object to JSON", e);
        }
    }

    public String ofuscarObjetoJson(String json) {
        return obfuscateJsonScoped(json, defaultScopeId());
    }

    String obfuscateJsonScoped(String json, String scopeId) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        try {
            JsonNode node = JSON_MAPPER.readTree(json);
            transformTextNodes(node, text -> obfuscateScoped(text, scopeId));
            return JSON_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new ObfuscationException("Error processing JSON", e);
        }
    }

    public <T> T restaurarObjeto(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        try {
            String restaurado = restaurar(json);
            return JSON_MAPPER.readValue(restaurado, clazz);
        } catch (JsonProcessingException e) {
            throw new ObfuscationException("Error deserializing JSON to object", e);
        }
    }

    public String restaurarObjetoJson(String json) {
        return restoreJsonScoped(json, defaultScopeId());
    }

    String restoreJsonScoped(String json, String scopeId) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        try {
            JsonNode node = JSON_MAPPER.readTree(json);
            transformTextNodes(node, text -> restoreScoped(text, scopeId));
            return JSON_MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new ObfuscationException("Error processing JSON", e);
        }
    }

    private void transformTextNodes(JsonNode node, java.util.function.UnaryOperator<String> transform) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            for (Map.Entry<String, JsonNode> entry : objectNode.properties()) {
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    entry.setValue(TextNode.valueOf(transform.apply(value.asText())));
                } else if (value.isObject() || value.isArray()) {
                    transformTextNodes(value, transform);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode element = arrayNode.get(i);
                if (element.isTextual()) {
                    arrayNode.set(i, new TextNode(transform.apply(element.asText())));
                } else if (element.isObject() || element.isArray()) {
                    transformTextNodes(element, transform);
                }
            }
        }
    }
}
