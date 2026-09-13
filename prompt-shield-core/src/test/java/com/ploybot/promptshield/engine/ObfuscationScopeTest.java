package com.ploybot.promptshield.engine;

import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ObfuscationScopeTest {

    @Test
    void roundTripWithinScope() {
        ObfuscationEngine engine = new ObfuscationEngine();
        ObfuscationScope scope = engine.forScope("conv-1");

        String obfuscated = scope.obfuscate("Mi DNI es 12345678Z y mi email juan@example.com");
        assertFalse(obfuscated.contains("12345678Z"));
        assertFalse(obfuscated.contains("juan@example.com"));

        String restored = scope.restore(obfuscated);
        assertEquals("Mi DNI es 12345678Z y mi email juan@example.com", restored);
    }

    @Test
    void tagsAreIsolatedBetweenScopes() {
        ObfuscationEngine engine = new ObfuscationEngine();
        ObfuscationScope scopeA = engine.forScope("conv-A");
        ObfuscationScope scopeB = engine.forScope("conv-B");

        String obfuscatedA = scope(scopeA, "El DNI es 12345678Z");

        // Another conversation must not be able to restore conv-A's tags
        String restoredByB = scopeB.restore(obfuscatedA);
        assertFalse(restoredByB.contains("12345678Z"),
                "conversation B must not restore values obfuscated in conversation A");

        // And the original scope restores normally
        assertEquals("El DNI es 12345678Z", scopeA.restore(obfuscatedA));
    }

    @Test
    void sameValueGetsDifferentIdsInDifferentScopes() {
        ObfuscationEngine engine = new ObfuscationEngine();
        String a = engine.forScope("conv-A").obfuscate("juan@example.com");
        String b = engine.forScope("conv-B").obfuscate("juan@example.com");
        assertNotEquals(a, b, "identifiers must not be linkable across conversations");
    }

    @Test
    void invalidChecksumMatchesAreNotObfuscated() {
        ObfuscationEngine engine = new ObfuscationEngine();
        ObfuscationScope scope = engine.forScope("conv-1");

        // 12345678A has an invalid control letter (valid would be Z)
        String result = scope.obfuscate("Referencia 12345678A pero DNI real 12345678Z");
        assertTrue(result.contains("12345678A"), "invalid DNI must be left untouched");
        assertFalse(result.contains("12345678Z"), "valid DNI must be obfuscated");
    }

    @Test
    void obfuscatesKnownStructuredValueWithoutRegisteredPattern() {
        ObfuscationScope scope = new ObfuscationEngine().forScope("customer-1");

        String tag = scope.obfuscateValue("Juan Perez", "NOMBRE");

        assertTrue(tag.matches("~REDACTED:NOMBRE#[a-f0-9]{6}~"));
        assertEquals("Juan Perez", scope.restore(tag));
        assertEquals(1, scope.removeMappings(tag));
        assertEquals(tag, scope.restore(tag));
    }

    @Test
    void collidingTruncatedIdsAreExtendedAndRestoreCorrectly() {
        // hashLength=1 gives only 16 possible ids: collisions are guaranteed
        ObfuscationConfig config = new ObfuscationConfig();
        config.setHashLength(1);
        ObfuscationEngine engine = new ObfuscationEngine(config, new InMemoryStorageService());
        ObfuscationScope scope = engine.forScope("conv-1");

        List<String> emails = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            emails.add("user" + i + "@example.com");
        }

        for (String email : emails) {
            String obfuscated = scope.obfuscate("Email: " + email);
            assertFalse(obfuscated.contains(email));
            assertEquals("Email: " + email, scope.restore(obfuscated),
                    "each value must restore to itself even when truncated ids collide");
        }
    }

    @Test
    void concurrentScopesDoNotInterfere() throws Exception {
        ObfuscationEngine engine = new ObfuscationEngine();
        int threads = 8;
        int iterations = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            String convId = "conv-" + t;
            String email = "user" + t + "@example.com";
            results.add(executor.submit(() -> {
                start.await();
                ObfuscationScope scope = engine.forScope(convId);
                for (int i = 0; i < iterations; i++) {
                    String obfuscated = scope.obfuscate("Contacto: " + email);
                    if (!("Contacto: " + email).equals(scope.restore(obfuscated))) {
                        return false;
                    }
                }
                return true;
            }));
        }

        start.countDown();
        for (Future<Boolean> result : results) {
            assertTrue(result.get(30, TimeUnit.SECONDS), "round-trip failed under concurrency");
        }
        executor.shutdownNow();
    }

    @Test
    void removeCreatedMappingsReleasesOnlyWhatTheInstanceCreated() {
        InMemoryStorageService storage = new InMemoryStorageService();
        ObfuscationEngine engine = new ObfuscationEngine(new ObfuscationConfig(), storage);

        // An earlier request of the same conversation already mapped the DNI
        ObfuscationScope earlier = engine.forScope("conv-1");
        String dniTag = earlier.obfuscate("12345678Z");

        ObfuscationScope request = engine.forScope("conv-1");
        String obfuscated = request.obfuscate("DNI 12345678Z, email juan@example.com");
        assertEquals(2, storage.size());

        assertEquals(1, request.removeCreatedMappings(), "only the email mapping was created by this request");
        assertEquals(0, request.removeCreatedMappings(), "a second call has nothing left to remove");
        assertEquals("12345678Z", earlier.restore(dniTag), "the pre-existing mapping must survive");
        assertTrue(request.restore(obfuscated).contains("~REDACTED:EMAIL#"));
    }

    @Test
    void ephemeralScopesDoNotAccumulateWhenReleased() {
        InMemoryStorageService storage = new InMemoryStorageService();
        ObfuscationEngine engine = new ObfuscationEngine(new ObfuscationConfig(), storage);

        for (int i = 0; i < 1_000; i++) {
            ObfuscationScope scope = engine.forScope("request-" + i);
            scope.restore(scope.obfuscate("dni 12345678Z mail a@b.com"));
            scope.removeCreatedMappings();
        }

        assertEquals(0, storage.size());
    }

    private String scope(ObfuscationScope scope, String text) {
        String obfuscated = scope.obfuscate(text);
        assertNotEquals(text, obfuscated);
        return obfuscated;
    }
}
