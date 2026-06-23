package com.ploybot.promptshield.storage.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploybot.promptshield.config.PromptShieldProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "prompt-shield.storage", name = "type", havingValue = "redis", matchIfMissing = false)
@EnableConfigurationProperties(PromptShieldProperties.class)
public class RedisStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @Qualifier("promptShieldStorageService")
    public RedisStorageService redisStorageService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            PromptShieldProperties properties) {
        return new RedisStorageService(redisTemplate, objectMapper, properties.getStorage().getRedis().getTtlHours());
    }
}
