package com.ploybot.promptshield.storage.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ploybot.promptshield.config.PromptShieldProperties;
import com.ploybot.promptshield.storage.StorageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration
@AutoConfigureBefore(name = {
        "com.ploybot.promptshield.spring.autoconfigure.ObfuscationAutoConfiguration",
        "com.ploybot.promptshield.spring.ai.autoconfigure.PromptShieldAdvisorAutoConfiguration",
        "com.ploybot.promptshield.proxy.autoconfigure.ApiProxyAutoConfiguration"
})
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "prompt-shield.storage", name = "type", havingValue = "redis", matchIfMissing = false)
@EnableConfigurationProperties(PromptShieldProperties.class)
public class RedisStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(StorageService.class)
    @Qualifier("promptShieldStorageService")
    public RedisStorageService redisStorageService(
            StringRedisTemplate redisTemplate,
            PromptShieldProperties properties) {
        // Dedicated mapper: the application context may only provide a Jackson 3
        // (tools.jackson) mapper, and app-level customizations must not affect
        // the persisted tag format.
        return new RedisStorageService(redisTemplate, new ObjectMapper(),
                properties.getStorage().getRedis().getTtlHours());
    }
}
