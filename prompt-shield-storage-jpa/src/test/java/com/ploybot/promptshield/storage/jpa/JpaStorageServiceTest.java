package com.ploybot.promptshield.storage.jpa;

import com.ploybot.promptshield.model.ObfuscationTag;
import com.ploybot.promptshield.storage.jpa.entity.ObfuscationTagEntity;
import com.ploybot.promptshield.storage.jpa.repository.ObfuscationTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaStorageServiceTest {

    @Mock
    private ObfuscationTagRepository repository;

    private JpaStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new JpaStorageService(repository, 24);
    }

    @Test
    void testStore() {
        // Arrange
        String hash = "abc123";
        ObfuscationTag tag = new ObfuscationTag("DNI", "abc123", "12345678Z");

        // Act
        storageService.store(hash, tag);

        // Assert
        verify(repository).save(argThat(entity -> 
            entity.getHash().equals(hash) &&
            entity.getType().equals("DNI") &&
            entity.getOriginalValue().equals("12345678Z")
        ));
    }

    @Test
    void testRetrieve() {
        // Arrange
        String hash = "abc123";
        ObfuscationTagEntity entity = new ObfuscationTagEntity(hash, "DNI", "12345678Z");
        when(repository.findById(hash)).thenReturn(Optional.of(entity));

        // Act
        Optional<ObfuscationTag> result = storageService.retrieve(hash);

        // Assert
        assertTrue(result.isPresent());
        assertEquals("DNI", result.get().getType());
        assertEquals("abc123", result.get().getHash());
        assertEquals("12345678Z", result.get().getOriginalValue());
    }

    @Test
    void testRetrieveNotFound() {
        // Arrange
        String hash = "nonexistent";
        when(repository.findById(hash)).thenReturn(Optional.empty());

        // Act
        Optional<ObfuscationTag> result = storageService.retrieve(hash);

        // Assert
        assertFalse(result.isPresent());
    }

    @Test
    void testContains() {
        // Arrange
        String hash = "abc123";
        when(repository.existsById(hash)).thenReturn(true);

        // Act
        boolean result = storageService.contains(hash);

        // Assert
        assertTrue(result);
    }

    @Test
    void testContainsNotFound() {
        // Arrange
        String hash = "nonexistent";
        when(repository.existsById(hash)).thenReturn(false);

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
        verify(repository).deleteById(hash);
    }

    @Test
    void testClear() {
        // Act
        storageService.clear();

        // Assert
        verify(repository).deleteAll();
    }

    @Test
    void testCleanupExpired() {
        // Arrange
        when(repository.deleteByCreatedAtBefore(any(LocalDateTime.class))).thenReturn(5);

        // Act
        int deleted = storageService.cleanupExpired();

        // Assert
        assertEquals(5, deleted);
        verify(repository).deleteByCreatedAtBefore(any(LocalDateTime.class));
    }

    @Test
    void testSize() {
        // Arrange
        when(repository.count()).thenReturn(10L);

        // Act
        long result = storageService.size();

        // Assert
        assertEquals(10L, result);
    }

    @Test
    void testCountByType() {
        // Arrange
        when(repository.countByType("DNI")).thenReturn(5L);

        // Act
        long result = storageService.countByType("DNI");

        // Assert
        assertEquals(5L, result);
    }
}
