package com.ploybot.promptshield.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ObfuscationTagJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void roundTripWithPlainObjectMapper() throws Exception {
        ObfuscationTag tag = new ObfuscationTag("DNI", "abc123", "12345678Z");

        String json = objectMapper.writeValueAsString(tag);
        ObfuscationTag restored = objectMapper.readValue(json, ObfuscationTag.class);

        assertEquals(tag.getType(), restored.getType());
        assertEquals(tag.getHash(), restored.getHash());
        assertEquals(tag.getOriginalValue(), restored.getOriginalValue());
        assertEquals(tag.getTag(), restored.getTag());
    }

    @Test
    void derivedTagPropertyIsNotSerialized() throws Exception {
        ObfuscationTag tag = new ObfuscationTag("DNI", "abc123", "12345678Z");

        String json = objectMapper.writeValueAsString(tag);

        assertFalse(json.contains("\"tag\""), "derived 'tag' property must not be persisted: " + json);
    }
}
