package com.ploybot.promptshield.spring.ai.autoconfigure;

import com.ploybot.promptshield.config.PromptShieldProperties;
import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.spring.ai.advisor.PromptShieldAdvisor;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import com.ploybot.promptshield.storage.StorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({ObfuscationEngine.class, PromptShieldAdvisor.class})
@ConditionalOnProperty(prefix = "prompt-shield.advisor", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PromptShieldProperties.class)
public class PromptShieldAdvisorAutoConfiguration {

    private final PromptShieldProperties properties;

    public PromptShieldAdvisorAutoConfiguration(PromptShieldProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObfuscationConfig promptShieldObfuscationConfig() {
        ObfuscationConfig config = new ObfuscationConfig();
        config.setHashAlgorithm(properties.getHashAlgorithm());
        config.setHashLength(properties.getHashLength());
        config.setEnabled(properties.isEnabled());
        config.setRedactedPrefix(properties.getRedactedPrefix());
        config.setTagSeparator(properties.getTagSeparator());
        config.setTagOpen(properties.getTagOpen());
        config.setTagClose(properties.getTagClose());
        config.setStorageType(properties.getStorageType());

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
        PromptShieldProperties.AdvisorProperties advisorProps = properties.getAdvisor();
        return new PromptShieldAdvisor(
                engine,
                advisorProps.getOrder(),
                advisorProps.isRestoreOnResponse(),
                advisorProps.isInjectSystemPrompt(),
                advisorProps.getSystemPrompt()
        );
    }
}
