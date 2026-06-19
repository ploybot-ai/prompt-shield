package com.obfuscador.hash;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HashGeneratorTest {

    @Test
    void generateHash() {
        HashGenerator generator = new HashGenerator();
        String hash = generator.generate("test");
        assertNotNull(hash);
        assertEquals(6, hash.length());
    }

    @Test
    void sameInputSameHash() {
        HashGenerator generator = new HashGenerator();
        String hash1 = generator.generate("12345678Z");
        String hash2 = generator.generate("12345678Z");
        assertEquals(hash1, hash2);
    }

    @Test
    void differentInputDifferentHash() {
        HashGenerator generator = new HashGenerator();
        String hash1 = generator.generate("12345678Z");
        String hash2 = generator.generate("87654321A");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void customLength() {
        HashGenerator generator = new HashGenerator("SHA-256", 10);
        String hash = generator.generate("test");
        assertEquals(10, hash.length());
    }
}
