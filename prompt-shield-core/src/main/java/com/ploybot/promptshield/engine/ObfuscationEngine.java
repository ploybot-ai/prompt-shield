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
import com.ploybot.promptshield.ner.DetectedEntity;
import com.ploybot.promptshield.registry.DataTypeRegistry;
import com.ploybot.promptshield.registry.SensitiveDataType;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import com.ploybot.promptshield.storage.StorageService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
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
        requireNonEmpty(config.getTagOpen(), "tagOpen");
        requireNonEmpty(config.getTagClose(), "tagClose");
        requireNonEmpty(config.getTagSeparator(), "tagSeparator");
        requireNonEmpty(config.getRedactedPrefix(), "redactedPrefix");
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

    private static void requireNonEmpty(String value, String name) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be null or empty");
        }
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
        return Pattern.compile(prefix + "(" + DataTypeRegistry.TYPE_NAME_REGEX + ")" + separator
                + "([a-f0-9]{" + config.getHashLength() + "," + MAX_ID_HEX_LENGTH + "})" + suffix);
    }

    public Pattern getTagPattern() {
        return tagPattern;
    }

    @SuppressWarnings("deprecation")
    private ObfuscationScope defaultScope() {
        return forScope(config.getScopeId());
    }

    public String ofuscar(String text) {
        return defaultScope().obfuscate(text);
    }

    public String ofuscar(String text, String typeName) {
        return defaultScope().obfuscate(text, typeName);
    }

    public String ofuscar(String text, SensitiveDataType type) {
        return ofuscar(text, type.getTypeName());
    }

    public String restaurar(String text) {
        return defaultScope().restore(text);
    }

    String obfuscateScoped(String text, ObfuscationScope scope) {
        return obfuscateTypes(text, registry.getRegisteredTypeNames(), scope);
    }

    String obfuscateScoped(String text, Collection<String> typeNames, ObfuscationScope scope) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        for (String typeName : typeNames) {
            if (!registry.isRegistered(typeName)) {
                throw new ObfuscationException("Type not registered: " + typeName);
            }
        }
        return obfuscateTypes(text, typeNames, scope);
    }

    String obfuscateValueScoped(String value, String typeName, ObfuscationScope scope) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (!DataTypeRegistry.isValidTypeName(typeName)) {
            throw new IllegalArgumentException("typeName must match " + DataTypeRegistry.TYPE_NAME_REGEX);
        }
        String id = assignId(typeName, value, scope);
        return formatTag(typeName, id);
    }

    /**
     * Detects every requested type on the original text in one pass and replaces the
     * matches at once. Tags already present in the text are left untouched, so no
     * pattern can corrupt a tag produced earlier.
     */
    private String obfuscateTypes(String text, Collection<String> typeNames, ObfuscationScope scope) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        List<DetectedEntity> entities = registry.detect(text, typeNames, tagRanges(text));
        if (entities.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder(text.length() + entities.size() * 24);
        int last = 0;
        for (DetectedEntity entity : entities) {
            result.append(text, last, entity.getStart());
            result.append(formatTag(entity.getType(), assignId(entity.getType(), entity.getValue(), scope)));
            last = entity.getEnd();
        }
        result.append(text, last, text.length());
        return result.toString();
    }

    private List<int[]> tagRanges(String text) {
        List<int[]> ranges = new ArrayList<>();
        Matcher matcher = tagPattern.matcher(text);
        while (matcher.find()) {
            ranges.add(new int[]{matcher.start(), matcher.end()});
        }
        return ranges;
    }

    /**
     * Assigns a stable identifier to the value within the scope. The id is a
     * truncated keyed HMAC; on the (rare) prefix collision with a different value it
     * is deterministically extended one hex character at a time until it is unique.
     */
    private String assignId(String typeName, String value, ObfuscationScope scope) {
        String scopeId = scope.getScopeId();
        String fullHash = hashGenerator.generateFull(scopeId + HMAC_INPUT_SEPARATOR + value);
        int maxLength = Math.min(fullHash.length(), MAX_ID_HEX_LENGTH);
        int length = Math.min(hashGenerator.getHashLength(), maxLength);

        while (true) {
            String id = fullHash.substring(0, length);
            String key = scopedKey(scopeId, id);
            Optional<ObfuscationTag> existing = storageService.storeIfAbsent(key, new ObfuscationTag(typeName, id, value));
            if (existing.isEmpty()) {
                scope.recordCreated(key);
                return id;
            }
            if (existing.get().getOriginalValue().equals(value)) {
                storageService.refresh(key);
                return id;
            }
            if (length >= maxLength) {
                throw new ObfuscationException("Unable to assign a unique tag id for type " + typeName);
            }
            length++;
        }
    }

    private String formatTag(String typeName, String id) {
        return config.getTagOpen() + config.getRedactedPrefix() + ":" + typeName + config.getTagSeparator() + id + config.getTagClose();
    }

    /** Returns the placeholder for the tag as it appears in obfuscated text. */
    public String formatTag(ObfuscationTag tag) {
        return formatTag(tag.getType(), tag.getHash());
    }

    String restoreScoped(String text, String scopeId) {
        return restoreScoped(text, scopeId, true, "");
    }

    /**
     * @param insideUrls     whether tags that are part of a URL are restored too
     * @param precedingText  text emitted right before {@code text} (streaming), used to
     *                       tell whether a tag at its very start continues a URL
     */
    String restoreScoped(String text, String scopeId, boolean insideUrls, CharSequence precedingText) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = tagPattern.matcher(text);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            if (!insideUrls && isInsideUrl(precedingText, text, matcher.start())) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
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

    private static final int MAX_URL_LOOKBACK = 2048;

    /**
     * Whether the tag starting at {@code index} belongs to a URL: the run of
     * characters before it (up to whitespace, a quote or an angle bracket) contains a
     * scheme separator ({@code ://}), {@code www.} or {@code mailto:}. A restored value
     * inside a link or a markdown image leaves the conversation as soon as the client
     * renders it.
     */
    static boolean isInsideUrl(CharSequence preceding, String text, int index) {
        StringBuilder token = new StringBuilder();
        int k = index - 1;
        while (token.length() < MAX_URL_LOOKBACK) {
            char c;
            if (k >= 0) {
                c = text.charAt(k);
            } else if (preceding != null && preceding.length() + k >= 0) {
                c = preceding.charAt(preceding.length() + k);
            } else {
                break;
            }
            if (Character.isWhitespace(c) || c == '"' || c == '\'' || c == '<' || c == '>' || c == '`') {
                break;
            }
            token.append(c);
            k--;
        }
        String run = token.reverse().toString().toLowerCase(java.util.Locale.ROOT);
        return run.contains("://") || run.contains("www.") || run.contains("mailto:");
    }

    public boolean containsTags(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return tagPattern.matcher(text).find();
    }

    public List<ObfuscationTag> extractTags(String text) {
        return defaultScope().extractTags(text);
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
        return SystemPromptGenerator.generate(config, language);
    }

    /**
     * Serializes the object to JSON and obfuscates it in the default scope. See
     * {@link ObfuscationScope#obfuscateObject(Object)}.
     */
    public String ofuscarObjeto(Object objeto) {
        return defaultScope().obfuscateObject(objeto);
    }

    public String ofuscarObjetoJson(String json) {
        return defaultScope().obfuscateJson(json);
    }

    public <T> T restaurarObjeto(String json, Class<T> clazz) {
        return defaultScope().restoreObject(json, clazz);
    }

    public String restaurarObjetoJson(String json) {
        return defaultScope().restoreJson(json);
    }

    String obfuscateObjectScoped(Object object, ObfuscationScope scope) {
        if (object == null) {
            return null;
        }
        try {
            return obfuscateJsonScoped(JSON_MAPPER.writeValueAsString(object), true, scope);
        } catch (JsonProcessingException e) {
            throw new ObfuscationException("Error serializing object to JSON", e);
        }
    }

    <T> T restoreObjectScoped(String json, Class<T> type, String scopeId) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return JSON_MAPPER.readValue(restoreJsonScoped(json, scopeId), type);
        } catch (JsonProcessingException e) {
            throw new ObfuscationException("Error deserializing JSON to object", e);
        }
    }

    String obfuscateJsonScoped(String json, boolean includeNumbersAndKeys, ObfuscationScope scope) {
        return transformJson(json, text -> obfuscateScoped(text, scope), includeNumbersAndKeys, includeNumbersAndKeys);
    }

    String restoreJsonScoped(String json, String scopeId) {
        return transformJson(json, text -> restoreScoped(text, scopeId), false, true);
    }

    private String transformJson(String json, UnaryOperator<String> transform, boolean numbers, boolean keys) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        try {
            JsonNode node = JSON_MAPPER.readTree(json);
            return JSON_MAPPER.writeValueAsString(transformNode(node, transform, numbers, keys));
        } catch (JsonProcessingException e) {
            throw new ObfuscationException("Error processing JSON", e);
        }
    }

    /**
     * Returns the transformed node. String values are always transformed; numbers and
     * object keys only when requested. A number becomes a string only if its text
     * actually changes.
     */
    private JsonNode transformNode(JsonNode node, UnaryOperator<String> transform, boolean numbers, boolean keys) {
        if (node.isTextual() || numbers && node.isNumber()) {
            String text = node.asText();
            String transformed = transform.apply(text);
            return transformed.equals(text) ? node : TextNode.valueOf(transformed);
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                array.set(i, transformNode(array.get(i), transform, numbers, keys));
            }
            return array;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Map<String, JsonNode> transformed = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : object.properties()) {
                String key = keys ? transform.apply(entry.getKey()) : entry.getKey();
                transformed.put(key, transformNode(entry.getValue(), transform, numbers, keys));
            }
            object.removeAll();
            object.setAll(transformed);
            return object;
        }
        return node;
    }
}
