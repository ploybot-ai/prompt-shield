package com.ploybot.promptshield.storage.redis;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "obfuscador.storage.redis")
public class RedisStorageProperties {

    private long ttlHours = 24;

    public long getTtlHours() {
        return ttlHours;
    }

    public void setTtlHours(long ttlHours) {
        this.ttlHours = ttlHours;
    }
}
