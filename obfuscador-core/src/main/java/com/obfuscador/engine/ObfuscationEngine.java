package com.obfuscador.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.obfuscador.exception.ObfuscationException;
import com.obfuscador.exception.TagNotFoundException;
import com.obfuscador.hash.HashGenerator;
import com.obfuscador.model.ObfuscationConfig;
import com.obfuscador.model.ObfuscationTag;
import com.obfuscador.registry.DataTypeRegistry;
import com.obfuscador.registry.SensitiveDataType;
import com.obfuscador.storage.InMemoryStorageService;
import com.obfuscador.storage.StorageService;

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

    private static final Pattern TAG_PATTERN = Pattern.compile("<([A-Z]+)_([a-f0-9]{6})>");

    public ObfuscationEngine(ObfuscationConfig config, StorageService storageService) {
        this.config = config;
        this.hashGenerator = new HashGenerator(config.getHashAlgorithm(), config.getHashLength());
        this.registry = new DataTypeRegistry();
        this.storageService = storageService;

        for (Map.Entry<String, ObfuscationConfig.CustomTypeConfig> entry : config.getCustomTypes().entrySet()) {
            registry.register(entry.getKey(), entry.getValue().getPattern());
        }
    }

    public ObfuscationEngine() {
        this(new ObfuscationConfig(), new InMemoryStorageService());
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
            ObfuscationTag tag = new ObfuscationTag(typeName, hash, matchedValue);

            if (!storageService.contains(hash)) {
                storageService.store(hash, tag);
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(tag.getTag()));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    public String restaurar(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Matcher matcher = TAG_PATTERN.matcher(text);
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
        return TAG_PATTERN.matcher(text).find();
    }

    public List<ObfuscationTag> extractTags(String text) {
        List<ObfuscationTag> tags = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tags;
        }

        Matcher matcher = TAG_PATTERN.matcher(text);
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
