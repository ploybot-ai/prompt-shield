package com.ploybot.spring.ai.autoconfigure;

import com.ploybot.engine.ObfuscationEngine;
import com.ploybot.model.ObfuscationConfig;
import com.ploybot.spring.ai.advisor.PromptShieldAdvisor;
import com.ploybot.storage.InMemoryStorageService;
import com.ploybot.storage.StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({ObfuscationEngine.class, PromptShieldAdvisor.class})
@ConditionalOnProperty(prefix = "prompt-shield.advisor", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PromptShieldAdvisorProperties.class)
public class PromptShieldAdvisorAutoConfiguration {

    private final PromptShieldAdvisorProperties properties;

    public PromptShieldAdvisorAutoConfiguration(PromptShieldAdvisorProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObfuscationConfig promptShieldObfuscationConfig() {
        ObfuscationConfig config = new ObfuscationConfig();
        config.setHashAlgorithm(properties.getHashAlgorithm());
        config.setHashLength(properties.getHashLength());
        config.setEnabled(properties.isEnabled());

        for (var entry : properties.getCustomTypes().entrySet()) {
            config.addCustomType(entry.getKey(), entry.getValue().getPattern());
        }

        return config;
    }

    @Bean
    @ConditionalOnMissingBean
    public StorageService promptShieldStorageService() {
        return new InMemoryStorageService();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObfuscationEngine promptShieldObfuscationEngine(ObfuscationConfig config, StorageService storageService) {
        return new ObfuscationEngine(config, storageService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptShieldAdvisor promptShieldAdvisor(ObfuscationEngine engine) {
        return new PromptShieldAdvisor(
                engine,
                properties.getOrder(),
                properties.isRestoreOnResponse()
        );
    }
}
