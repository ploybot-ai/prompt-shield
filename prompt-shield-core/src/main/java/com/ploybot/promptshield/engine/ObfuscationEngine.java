package com.ploybot.promptshield.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ploybot.promptshield.exception.ObfuscationException;
import com.ploybot.promptshield.exception.TagNotFoundException;
import com.ploybot.promptshield.hash.HashGenerator;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.model.ObfuscationTag;
import com.ploybot.promptshield.registry.DataTypeRegistry;
import com.ploybot.promptshield.registry.SensitiveDataType;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import com.ploybot.promptshield.storage.StorageService;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ObfuscationEngine {

    private final HashGenerator hashGenerator;
    private final DataTypeRegistry registry;
    private final StorageService storageService;
    private final ObfuscationConfig config;

    private Pattern tagPattern;

    public ObfuscationEngine(ObfuscationConfig config, StorageService storageService) {
        this.config = config;
        this.hashGenerator = new HashGenerator(config.getHashAlgorithm(), config.getHashLength());
        this.registry = new DataTypeRegistry();
        this.storageService = storageService;
        this.tagPattern = buildTagPattern();

        for (Map.Entry<String, ObfuscationConfig.CustomTypeConfig> entry : config.getCustomTypes().entrySet()) {
            registry.register(entry.getKey(), entry.getValue().getPattern());
        }
    }

    public ObfuscationEngine() {
        this(new ObfuscationConfig(), new InMemoryStorageService());
    }

    private Pattern buildTagPattern() {
        String prefix = Pattern.quote("{{" + config.getRedactedPrefix() + ":");
        String separator = Pattern.quote(config.getTagSeparator());
        String suffix = Pattern.quote("}}");
        return Pattern.compile(prefix + "([A-Z_]+)" + separator + "([a-f0-9]{" + config.getHashLength() + "})" + suffix);
    }

    public String ofuscar(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;
        List<String> processedTypes = new ArrayList<>(registry.getRegisteredTypeNames());

        for (String typeName : processedTypes) {
            Pattern pattern = registry.getPattern(typeName);
            if (pattern != null) {
                result = ofuscarTipo(result, typeName, pattern);
            }
        }

        return result;
    }

    public String ofuscar(String text, String typeName) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Pattern pattern = registry.getPattern(typeName);
        if (pattern == null) {
            throw new ObfuscationException("Type not registered: " + typeName);
        }

        return ofuscarTipo(text, typeName, pattern);
    }

    public String ofuscar(String text, SensitiveDataType type) {
        return ofuscar(text, type.getTypeName());
    }

    private String ofuscarTipo(String text, String typeName, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String matchedValue = matcher.group();
            String hash = hashGenerator.generate(matchedValue);
            String tag = formatTag(typeName, hash);

            if (!storageService.contains(hash)) {
                ObfuscationTag obfuscationTag = new ObfuscationTag(typeName, hash, matchedValue);
                storageService.store(hash, obfuscationTag);
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(tag));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private String formatTag(String typeName, String hash) {
        return "{{" + config.getRedactedPrefix() + ":" + typeName + config.getTagSeparator() + hash + "}}";
    }

    public String restaurar(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = tagPattern.matcher(text);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String type = matcher.group(1);
            String hash = matcher.group(2);

            Optional<ObfuscationTag> tagOpt = storageService.retrieve(hash);
            if (tagOpt.isPresent()) {
                String originalValue = tagOpt.get().getOriginalValue();
                matcher.appendReplacement(result, Matcher.quoteReplacement(originalValue));
            } else {
                throw new TagNotFoundException(hash);
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
        List<ObfuscationTag> tags = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tags;
        }

        Matcher matcher = tagPattern.matcher(text);
        while (matcher.find()) {
            String type = matcher.group(1);
            String hash = matcher.group(2);
            storageService.retrieve(hash).ifPresent(tags::add);
        }

        return tags;
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

    public String ofuscarObjeto(Object objeto) {
        if (objeto == null) {
            return null;
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            String json = mapper.writeValueAsString(objeto);
            String ofuscado = ofuscar(json);
            return ofuscado;
        } catch (JsonProcessingException e) {
            throw new ObfuscationException("Error serializing object to JSON", e);
        }
    }

    public String ofuscarObjetoJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode node = mapper.readTree(json);
            ofuscarNodo(node);
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new ObfuscationException("Error processing JSON", e);
        }
    }

    private void ofuscarNodo(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    String ofuscado = ofuscar(value.asText());
                    objectNode.put(entry.getKey(), ofuscado);
                } else if (value.isObject() || value.isArray()) {
                    ofuscarNodo(value);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode element = arrayNode.get(i);
                if (element.isTextual()) {
                    arrayNode.set(i, new TextNode(ofuscar(element.asText())));
                } else if (element.isObject() || element.isArray()) {
                    ofuscarNodo(element);
                }
            }
        }
    }

    public <T> T restaurarObjeto(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            String restaurado = restaurar(json);
            return mapper.readValue(restaurado, clazz);
        } catch (JsonProcessingException e) {
            throw new ObfuscationException("Error deserializing JSON to object", e);
        }
    }

    public String restaurarObjetoJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode node = mapper.readTree(json);
            restaurarNodo(node);
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new ObfuscationException("Error processing JSON", e);
        }
    }

    private void restaurarNodo(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                    String restaurado = restaurar(value.asText());
                    objectNode.put(entry.getKey(), restaurado);
                } else if (value.isObject() || value.isArray()) {
                    restaurarNodo(value);
                }
            }
        } else if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (int i = 0; i < arrayNode.size(); i++) {
                JsonNode element = arrayNode.get(i);
                if (element.isTextual()) {
                    arrayNode.set(i, new TextNode(restaurar(element.asText())));
                } else if (element.isObject() || element.isArray()) {
                    restaurarNodo(element);
                }
            }
        }
    }
}
