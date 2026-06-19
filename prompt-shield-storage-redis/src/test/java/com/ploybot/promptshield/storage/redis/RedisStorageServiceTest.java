package com.ploybot.promptshield.storage.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploybot.promptshield.model.ObfuscationTag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

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
        verify(valueOperations).set(eq("prompt-shield:tag:abc123"), eq(json), eq(24L), eq(TimeUnit.HOURS));
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
        when(redisTemplate.keys("prompt-shield:*")).thenReturn(java.util.Set.of("key1", "key2"));

        // Act
        storageService.clear();

        // Assert
        verify(redisTemplate).delete(java.util.Set.of("key1", "key2"));
    }

    @Test
    void testClearNoKeys() {
        // Arrange
        when(redisTemplate.keys("prompt-shield:*")).thenReturn(java.util.Collections.emptySet());

        // Act
        storageService.clear();

        // Assert
        verify(redisTemplate, never()).delete(anyCollection());
    }

    @Test
    void testSize() {
        // Arrange
        when(redisTemplate.keys("prompt-shield:tag:*")).thenReturn(java.util.Set.of("key1", "key2", "key3"));

        // Act
        long result = storageService.size();

        // Assert
        assertEquals(3, result);
    }

    @Test
    void testSizeNoKeys() {
        // Arrange
        when(redisTemplate.keys("prompt-shield:tag:*")).thenReturn(null);

        // Act
        long result = storageService.size();

        // Assert
        assertEquals(0, result);
    }
}
