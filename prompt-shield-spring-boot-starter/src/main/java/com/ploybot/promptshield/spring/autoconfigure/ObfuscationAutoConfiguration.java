package com.ploybot.promptshield.spring.autoconfigure;

import com.ploybot.promptshield.config.PromptShieldProperties;
import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.spring.interceptor.ObfuscationInterceptor;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import com.ploybot.promptshield.storage.StorageService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

@AutoConfiguration
@ConditionalOnClass(ObfuscationEngine.class)
@ConditionalOnProperty(prefix = "prompt-shield", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PromptShieldProperties.class)
public class ObfuscationAutoConfiguration {

    private final PromptShieldProperties properties;

    public ObfuscationAutoConfiguration(PromptShieldProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObfuscationConfig obfuscationConfig() {
        return properties.toObfuscationConfig();
    }

    @Bean
    @ConditionalOnMissingBean(StorageService.class)
    @ConditionalOnProperty(prefix = "prompt-shield.storage", name = "type", havingValue = "memory", matchIfMissing = true)
    public StorageService promptShieldStorageService() {
        return new InMemoryStorageService(Duration.ofHours(properties.getStorage().getMemory().getTtlHours()));
    }

    /**
     * Uses the single {@link StorageService} in the context, whatever its bean name; if
     * there are several, the one named {@code promptShieldStorageService} or marked
     * {@code @Primary} wins.
     */
    @Bean
    @ConditionalOnMissingBean
    public ObfuscationEngine obfuscationEngine(ObfuscationConfig config, StorageService promptShieldStorageService) {
        return new ObfuscationEngine(config, promptShieldStorageService);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnProperty(prefix = "prompt-shield.annotation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ObfuscationInterceptor obfuscationInterceptor(ObfuscationEngine engine) {
        return new ObfuscationInterceptor(engine);
    }
}
