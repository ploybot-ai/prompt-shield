package com.ploybot.promptshield.ner.regex;

import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.ner.DetectedEntity;
import com.ploybot.promptshield.ner.EntityDetector;
import com.ploybot.promptshield.registry.SensitiveDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexEntityDetector implements EntityDetector {

    private final ObfuscationConfig config;

    public RegexEntityDetector(ObfuscationConfig config) {
        this.config = config;
    }

    @Override
    public List<DetectedEntity> detect(String text) {
        List<DetectedEntity> entities = new ArrayList<>();

        // Detect built-in types
        for (SensitiveDataType dataType : SensitiveDataType.values()) {
            detectByPattern(text, dataType.name(), dataType.getPattern(), entities);
        }

        // Detect custom types
        if (config != null && config.getCustomPatterns() != null) {
            for (Map.Entry<String, String> entry : config.getCustomPatterns().entrySet()) {
                detectByPattern(text, entry.getKey(), entry.getValue(), entities);
            }
        }

        return entities;
    }

    private void detectByPattern(String text, String type, String pattern, List<DetectedEntity> entities) {
        try {
            Pattern regex = Pattern.compile(pattern);
            Matcher matcher = regex.matcher(text);
            while (matcher.find()) {
                entities.add(new DetectedEntity(
                        type,
                        matcher.group(),
                        matcher.start(),
                        matcher.end()
                ));
            }
        } catch (Exception e) {
            // Skip invalid patterns
        }
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
