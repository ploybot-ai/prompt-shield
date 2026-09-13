package com.ploybot.promptshield.storage.jpa;

import com.ploybot.promptshield.model.ObfuscationTag;
import com.ploybot.promptshield.storage.StorageService;
import com.ploybot.promptshield.storage.jpa.entity.ObfuscationTagEntity;
import com.ploybot.promptshield.storage.jpa.repository.ObfuscationTagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Relational storage. Keys are stored as their SHA-256 so scope ids of any length fit
 * the 64-character primary key; mappings older than the time-to-live (counted from
 * their last use) are ignored and removed by {@link #cleanupExpired()}.
 *
 * <p>{@link #storeIfAbsent} is atomic within one JVM. Across instances, two different
 * values would have to collide on the same truncated keyed hash at the same moment.
 */
public class JpaStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(JpaStorageService.class);
    private static final int LOCK_STRIPES = 64;
    private static final int LEGACY_KEY_MAX_LENGTH = 64;
    /** Minimum age before a reused mapping's timestamp is written again. */
    private static final Duration REFRESH_GRANULARITY = Duration.ofMinutes(5);

    private final ObfuscationTagRepository repository;
    private final long ttlHours;
    private final TransactionOperations transactions;
    private final Clock clock;
    private final Object[] locks = new Object[LOCK_STRIPES];

    /**
     * For a Spring Data repository that is already transactional (e.g. one created by
     * repository scanning).
     */
    public JpaStorageService(ObfuscationTagRepository repository, long ttlHours) {
        this(repository, ttlHours, TransactionOperations.withoutTransaction(), Clock.systemDefaultZone());
    }

    public JpaStorageService(ObfuscationTagRepository repository, long ttlHours, PlatformTransactionManager transactionManager) {
        this(repository, ttlHours, new TransactionTemplate(transactionManager), Clock.systemDefaultZone());
    }

    JpaStorageService(ObfuscationTagRepository repository, long ttlHours, TransactionOperations transactions, Clock clock) {
        if (ttlHours <= 0) {
            throw new IllegalArgumentException("prompt-shield.storage.jpa.ttl-hours must be greater than zero");
        }
        this.repository = repository;
        this.ttlHours = ttlHours;
        this.transactions = transactions;
        this.clock = clock;
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new Object();
        }
    }

    @Override
    public void store(String key, ObfuscationTag tag) {
        transactions.executeWithoutResult(status -> save(key, tag));
        logger.debug("Stored obfuscation tag");
    }

    @Override
    public Optional<ObfuscationTag> storeIfAbsent(String key, ObfuscationTag tag) {
        synchronized (locks[Math.floorMod(key.hashCode(), LOCK_STRIPES)]) {
            return transactions.execute(status -> {
                Optional<ObfuscationTag> existing = find(key);
                if (existing.isEmpty()) {
                    // Replaces an expired row with the same digest, if any
                    save(key, tag);
                }
                return existing;
            });
        }
    }

    @Override
    public void refresh(String key) {
        LocalDateTime now = now();
        transactions.executeWithoutResult(status ->
                repository.touch(digest(key), now, now.minus(REFRESH_GRANULARITY)));
    }

    @Override
    public Optional<ObfuscationTag> retrieve(String key) {
        return transactions.execute(status -> find(key));
    }

    @Override
    public boolean contains(String key) {
        return retrieve(key).isPresent();
    }

    @Override
    public void remove(String key) {
        transactions.executeWithoutResult(status -> {
            repository.deleteById(digest(key));
            if (key.length() <= LEGACY_KEY_MAX_LENGTH) {
                repository.deleteById(key);
            }
        });
        logger.debug("Removed obfuscation tag");
    }

    @Override
    public void clear() {
        transactions.executeWithoutResult(status -> repository.deleteAll());
        logger.debug("Cleared all obfuscation tags");
    }

    public int cleanupExpired() {
        LocalDateTime cutoff = cutoff();
        Integer deleted = transactions.execute(status -> repository.deleteByCreatedAtBefore(cutoff));
        logger.debug("Cleaned up {} expired obfuscation tags", deleted);
        return deleted != null ? deleted : 0;
    }

    public long size() {
        return repository.count();
    }

    public long countByType(String type) {
        return repository.countByType(type);
    }

    private void save(String key, ObfuscationTag tag) {
        ObfuscationTagEntity entity = new ObfuscationTagEntity(digest(key), tag.getType(), tag.getOriginalValue());
        entity.setCreatedAt(now());
        repository.save(entity);
    }

    private Optional<ObfuscationTag> find(String key) {
        Optional<ObfuscationTagEntity> entity = repository.findById(digest(key));
        if (entity.isEmpty() && key.length() <= LEGACY_KEY_MAX_LENGTH) {
            // Rows written before 1.4 used the raw key
            entity = repository.findById(key);
        }
        LocalDateTime cutoff = cutoff();
        return entity
                .filter(e -> e.getCreatedAt() == null || !e.getCreatedAt().isBefore(cutoff))
                .map(e -> new ObfuscationTag(e.getType(), idOf(key), e.getOriginalValue()));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private LocalDateTime cutoff() {
        return now().minusHours(ttlHours);
    }

    /** The tag id is the part of the scoped key after the last colon. */
    private static String idOf(String key) {
        return key.substring(key.lastIndexOf(':') + 1);
    }

    static String digest(String key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
