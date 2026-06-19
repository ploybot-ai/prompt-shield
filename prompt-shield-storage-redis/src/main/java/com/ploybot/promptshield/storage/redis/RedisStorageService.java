package com.ploybot.promptshield.storage.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploybot.promptshield.model.ObfuscationTag;
import com.ploybot.promptshield.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class RedisStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(RedisStorageService.class);
    private static final String KEY_PREFIX = "prompt-shield:";
    private static final String TAG_KEY_PREFIX = "prompt-shield:tag:";

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
            redisTemplate.opsForValue().set(key, json, ttlHours, TimeUnit.HOURS);
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
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            logger.debug("Cleared all obfuscation tags");
        }
    }

    public long size() {
        var keys = redisTemplate.keys(TAG_KEY_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }
}
