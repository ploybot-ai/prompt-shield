package com.ploybot.promptshield.ner.regex;

import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.ner.DetectedEntity;
import com.ploybot.promptshield.ner.EntityDetector;
import com.ploybot.promptshield.registry.DataTypeRegistry;

import java.util.List;
import java.util.Map;

/**
 * Detects the same entities the obfuscation engine would replace: built-in and
 * custom types, honouring {@code serviceKeysEnabled}, checksum validation and type
 * priority (overlapping matches are resolved in favour of the higher-priority type).
 */
public class RegexEntityDetector implements EntityDetector {

    private final DataTypeRegistry registry;

    public RegexEntityDetector(ObfuscationConfig config) {
        ObfuscationConfig effective = config != null ? config : new ObfuscationConfig();
        this.registry = new DataTypeRegistry(effective.isServiceKeysEnabled(), effective.isChecksumValidationEnabled());
        for (Map.Entry<String, String> entry : effective.getCustomPatterns().entrySet()) {
            registry.register(entry.getKey(), entry.getValue());
        }
    }

    public RegexEntityDetector(DataTypeRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<DetectedEntity> detect(String text) {
        return registry.detect(text);
    }

    @Override
    public String getName() {
        return "Regex";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
