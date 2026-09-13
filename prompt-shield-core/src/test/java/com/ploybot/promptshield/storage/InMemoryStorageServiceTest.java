package com.ploybot.promptshield.storage;

import com.ploybot.promptshield.model.ObfuscationTag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryStorageServiceTest {

    private final AtomicLong clock = new AtomicLong();
    private final InMemoryStorageService storage = new InMemoryStorageService(Duration.ofHours(1), clock::get);

    @Test
    void storeIfAbsentKeepsTheFirstMapping() {
        ObfuscationTag first = new ObfuscationTag("DNI", "abc123", "12345678Z");
        ObfuscationTag second = new ObfuscationTag("DNI", "abc123", "87654321X");

        assertTrue(storage.storeIfAbsent("s:abc123", first).isEmpty());
        assertEquals("12345678Z", storage.storeIfAbsent("s:abc123", second).orElseThrow().getOriginalValue());
        assertEquals("12345678Z", storage.retrieve("s:abc123").orElseThrow().getOriginalValue());
    }

    @Test
    void entriesExpireAfterTheTtl() {
        storage.store("s:1", new ObfuscationTag("DNI", "1", "12345678Z"));
        clock.addAndGet(Duration.ofMinutes(59).toNanos());
        assertTrue(storage.contains("s:1"));

        clock.addAndGet(Duration.ofMinutes(2).toNanos());
        assertFalse(storage.contains("s:1"));
        assertTrue(storage.storeIfAbsent("s:1", new ObfuscationTag("DNI", "1", "87654321X")).isEmpty(),
                "an expired mapping must not block a new one");
    }

    @Test
    void refreshExtendsTheTtl() {
        storage.store("s:1", new ObfuscationTag("DNI", "1", "12345678Z"));
        clock.addAndGet(Duration.ofMinutes(50).toNanos());
        storage.refresh("s:1");
        clock.addAndGet(Duration.ofMinutes(50).toNanos());

        assertTrue(storage.contains("s:1"), "a mapping still in use must not expire");
    }

    @Test
    void expiredEntriesArePurgedOnWrite() {
        for (int i = 0; i < 100; i++) {
            storage.store("s:" + i, new ObfuscationTag("DNI", String.valueOf(i), "v" + i));
        }
        clock.addAndGet(Duration.ofHours(2).toNanos());
        storage.store("s:new", new ObfuscationTag("DNI", "new", "v"));

        assertEquals(1, storage.size());
    }

    @Test
    void withoutTtlEntriesNeverExpire() {
        InMemoryStorageService unbounded = new InMemoryStorageService(null, clock::get);
        unbounded.store("s:1", new ObfuscationTag("DNI", "1", "12345678Z"));
        clock.addAndGet(Duration.ofDays(365).toNanos());

        assertTrue(unbounded.contains("s:1"));
    }
}
