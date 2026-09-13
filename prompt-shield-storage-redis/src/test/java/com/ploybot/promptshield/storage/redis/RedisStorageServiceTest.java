package com.ploybot.promptshield.storage.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploybot.promptshield.model.ObfuscationTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisStorageServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    private RedisStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new RedisStorageService(redisTemplate, objectMapper, 24);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @SuppressWarnings("unchecked")
    private void mockScan(String... keys) {
        Cursor<String> cursor = mock(Cursor.class);
        Boolean[] hasNextTail = new Boolean[keys.length];
        for (int i = 0; i < keys.length; i++) {
            hasNextTail[i] = i < keys.length - 1;
        }
        if (keys.length == 0) {
            when(cursor.hasNext()).thenReturn(false);
        } else {
            when(cursor.hasNext()).thenReturn(true, hasNextTail);
            String[] tail = new String[keys.length - 1];
            System.arraycopy(keys, 1, tail, 0, keys.length - 1);
            when(cursor.next()).thenReturn(keys[0], tail);
        }
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);
    }

    @Test
    void testStore() throws Exception {
        // Arrange
        String hash = "abc123";
        ObfuscationTag tag = new ObfuscationTag("DNI", "abc123", "12345678Z");
        String json = "{\"type\":\"DNI\",\"hash\":\"abc123\",\"originalValue\":\"12345678Z\"}";
        when(objectMapper.writeValueAsString(tag)).thenReturn(json);

        // Act
        storageService.store(hash, tag);

        // Assert
        verify(valueOperations).set(eq("prompt-shield:tag:abc123"), eq(json), eq(Duration.ofHours(24)));
    }

    @Test
    void storeIfAbsentUsesSetNx() throws Exception {
        ObfuscationTag tag = new ObfuscationTag("DNI", "abc123", "12345678Z");
        String json = "{\"type\":\"DNI\",\"hash\":\"abc123\",\"originalValue\":\"12345678Z\"}";
        when(objectMapper.writeValueAsString(tag)).thenReturn(json);
        when(valueOperations.setIfAbsent("prompt-shield:tag:s:abc123", json, Duration.ofHours(24))).thenReturn(true);

        assertTrue(storageService.storeIfAbsent("s:abc123", tag).isEmpty());
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void storeIfAbsentReturnsTheExistingMapping() throws Exception {
        ObfuscationTag tag = new ObfuscationTag("DNI", "abc123", "87654321X");
        ObfuscationTag existing = new ObfuscationTag("DNI", "abc123", "12345678Z");
        String existingJson = "{\"type\":\"DNI\",\"hash\":\"abc123\",\"originalValue\":\"12345678Z\"}";
        when(objectMapper.writeValueAsString(tag)).thenReturn("new");
        when(valueOperations.setIfAbsent("prompt-shield:tag:s:abc123", "new", Duration.ofHours(24))).thenReturn(false);
        when(valueOperations.get("prompt-shield:tag:s:abc123")).thenReturn(existingJson);
        when(objectMapper.readValue(existingJson, ObfuscationTag.class)).thenReturn(existing);

        assertEquals(Optional.of(existing), storageService.storeIfAbsent("s:abc123", tag));
    }

    @Test
    void refreshExtendsTheTtl() {
        storageService.refresh("s:abc123");

        verify(redisTemplate).expire("prompt-shield:tag:s:abc123", Duration.ofHours(24));
    }

    @Test
    void nonPositiveTtlIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RedisStorageService(redisTemplate, objectMapper, 0));
    }

    @Test
    void testRetrieve() throws Exception {
        // Arrange
        String hash = "abc123";
        String json = "{\"type\":\"DNI\",\"hash\":\"abc123\",\"originalValue\":\"12345678Z\"}";
        ObfuscationTag expectedTag = new ObfuscationTag("DNI", "abc123", "12345678Z");

        when(valueOperations.get("prompt-shield:tag:abc123")).thenReturn(json);
        when(objectMapper.readValue(json, ObfuscationTag.class)).thenReturn(expectedTag);

        // Act
        Optional<ObfuscationTag> result = storageService.retrieve(hash);

        // Assert
        assertTrue(result.isPresent());
        assertEquals(expectedTag, result.get());
    }

    @Test
    void testRetrieveNotFound() {
        // Arrange
        String hash = "nonexistent";
        when(valueOperations.get("prompt-shield:tag:nonexistent")).thenReturn(null);

        // Act
        Optional<ObfuscationTag> result = storageService.retrieve(hash);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testContains() {
        // Arrange
        String hash = "abc123";
        when(redisTemplate.hasKey("prompt-shield:tag:abc123")).thenReturn(true);

        // Act
        boolean result = storageService.contains(hash);

        // Assert
        assertTrue(result);
    }

    @Test
    void testContainsNotFound() {
        // Arrange
        String hash = "nonexistent";
        when(redisTemplate.hasKey("prompt-shield:tag:nonexistent")).thenReturn(false);

        // Act
        boolean result = storageService.contains(hash);

        // Assert
        assertFalse(result);
    }

    @Test
    void testRemove() {
        // Arrange
        String hash = "abc123";

        // Act
        storageService.remove(hash);

        // Assert
        verify(redisTemplate).delete("prompt-shield:tag:abc123");
    }

    @Test
    void testClear() {
        // Arrange
        mockScan("key1", "key2");

        // Act
        storageService.clear();

        // Assert
        verify(redisTemplate).delete(List.of("key1", "key2"));
    }

    @Test
    void testClearNoKeys() {
        // Arrange
        mockScan();

        // Act
        storageService.clear();

        // Assert
        verify(redisTemplate, never()).delete(anyCollection());
    }

    @Test
    void testSize() {
        // Arrange
        mockScan("key1", "key2", "key3");

        // Act
        long result = storageService.size();

        // Assert
        assertEquals(3, result);
    }

    @Test
    void testSizeNoKeys() {
        // Arrange
        mockScan();

        // Act
        long result = storageService.size();

        // Assert
        assertEquals(0, result);
    }
}
