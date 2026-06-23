package com.ploybot.promptshield.spring.autoconfigure;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import com.ploybot.promptshield.storage.StorageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(ObfuscationEngine.class)
@ConditionalOnProperty(prefix = "obfuscador", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ObfuscationProperties.class)
public class ObfuscationAutoConfiguration {

    private final ObfuscationProperties properties;

    public ObfuscationAutoConfiguration(ObfuscationProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObfuscationConfig obfuscationConfig() {
        ObfuscationConfig config = new ObfuscationConfig();
        config.setHashAlgorithm(properties.getHashAlgorithm());
        config.setHashLength(properties.getHashLength());
        config.setStorageType(properties.getStorageType());
        config.setEnabled(properties.isEnabled());
        config.setRedactedPrefix(properties.getRedactedPrefix());
        config.setTagSeparator(properties.getTagSeparator());

        for (var entry : properties.getCustomTypes().entrySet()) {
            config.addCustomType(entry.getKey(), entry.getValue().getPattern());
        }

        return config;
    }

    @Bean
    @ConditionalOnMissingBean
    @Qualifier("promptShieldStorageService")
    public StorageService promptShieldStorageService() {
        return new InMemoryStorageService();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObfuscationEngine obfuscationEngine(ObfuscationConfig config, @Qualifier("promptShieldStorageService") StorageService storageService) {
        return new ObfuscationEngine(config, storageService);
    }
}
