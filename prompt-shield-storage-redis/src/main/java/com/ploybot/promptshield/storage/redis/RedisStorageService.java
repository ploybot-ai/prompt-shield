package com.ploybot.promptshield.storage.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploybot.promptshield.model.ObfuscationTag;
import com.ploybot.promptshield.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RedisStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(RedisStorageService.class);
    private static final String KEY_PREFIX = "prompt-shield:";
    private static final String TAG_KEY_PREFIX = "prompt-shield:tag:";
    private static final int SCAN_BATCH_SIZE = 500;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final long ttlHours;

    public RedisStorageService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, long ttlHours) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttlHours = ttlHours;
    }

    @Override
    public void store(String hash, ObfuscationTag tag) {
        try {
            String json = objectMapper.writeValueAsString(tag);
            String key = TAG_KEY_PREFIX + hash;
            redisTemplate.opsForValue().set(key, json, Duration.ofHours(ttlHours));
            logger.debug("Stored obfuscation tag for hash: {}", hash);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize obfuscation tag for hash: {}", hash, e);
            throw new RuntimeException("Failed to store obfuscation tag", e);
        }
    }

    @Override
    public Optional<ObfuscationTag> retrieve(String hash) {
        String key = TAG_KEY_PREFIX + hash;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            ObfuscationTag tag = objectMapper.readValue(json, ObfuscationTag.class);
            return Optional.of(tag);
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize obfuscation tag for hash: {}", hash, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean contains(String hash) {
        String key = TAG_KEY_PREFIX + hash;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    @Override
    public void remove(String hash) {
        String key = TAG_KEY_PREFIX + hash;
        redisTemplate.delete(key);
        logger.debug("Removed obfuscation tag for hash: {}", hash);
    }

    @Override
    public void clear() {
        // SCAN instead of KEYS: KEYS blocks Redis on large keyspaces
        long deleted = 0;
        List<String> batch = new ArrayList<>(SCAN_BATCH_SIZE);
        try (Cursor<String> cursor = scan(KEY_PREFIX + "*")) {
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= SCAN_BATCH_SIZE) {
                    deleted += delete(batch);
                    batch.clear();
                }
            }
        }
        deleted += delete(batch);
        logger.debug("Cleared {} obfuscation tags", deleted);
    }

    public long size() {
        long count = 0;
        try (Cursor<String> cursor = scan(TAG_KEY_PREFIX + "*")) {
            while (cursor.hasNext()) {
                cursor.next();
                count++;
            }
        }
        return count;
    }

    private Cursor<String> scan(String pattern) {
        return redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH_SIZE).build());
    }

    private long delete(List<String> keys) {
        if (keys.isEmpty()) {
            return 0;
        }
        Long deleted = redisTemplate.delete(keys);
        return deleted != null ? deleted : 0;
    }
}
