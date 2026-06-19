package com.obfuscador.storage;

import com.obfuscador.model.ObfuscationTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryStorageServiceTest {

    private InMemoryStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new InMemoryStorageService();
    }

    @Test
    void storeAndRetrieve() {
        ObfuscationTag tag = new ObfuscationTag("DNI", "abc123", "12345678Z");
        storageService.store("abc123", tag);

        Optional<ObfuscationTag> retrieved = storageService.retrieve("abc123");
        assertTrue(retrieved.isPresent());
        assertEquals("DNI", retrieved.get().getType());
        assertEquals("12345678Z", retrieved.get().getOriginalValue());
    }

    @Test
    void contains() {
        ObfuscationTag tag = new ObfuscationTag("DNI", "abc123", "12345678Z");
        storageService.store("abc123", tag);

        assertTrue(storageService.contains("abc123"));
        assertFalse(storageService.contains("xyz789"));
    }

    @Test
    void remove() {
        ObfuscationTag tag = new ObfuscationTag("DNI", "abc123", "12345678Z");
        storageService.store("abc123", tag);
        storageService.remove("abc123");

        assertFalse(storageService.contains("abc123"));
    }

    @Test
    void clear() {
        storageService.store("abc123", new ObfuscationTag("DNI", "abc123", "12345678Z"));
        storageService.store("xyz789", new ObfuscationTag("NIE", "xyz789", "X1234567A"));

        storageService.clear();
        assertEquals(0, storageService.size());
    }

    @Test
    void size() {
        assertEquals(0, storageService.size());
        storageService.store("abc123", new ObfuscationTag("DNI", "abc123", "12345678Z"));
        assertEquals(1, storageService.size());
    }
}
