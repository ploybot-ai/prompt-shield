package com.ploybot.promptshield.spring.ai.autoconfigure;

import com.ploybot.promptshield.config.PromptShieldProperties;
import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.spring.ai.advisor.PromptShieldAdvisor;
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
        return properties.toObfuscationConfig();
    }

    @Bean
    @ConditionalOnMissingBean(StorageService.class)
    @ConditionalOnProperty(prefix = "prompt-shield.storage", name = "type", havingValue = "memory", matchIfMissing = true)
    public StorageService promptShieldStorageService() {
        return new InMemoryStorageService(Duration.ofHours(properties.getStorage().getMemory().getTtlHours()));
    }

    @Bean
    @ConditionalOnMissingBean
    public ObfuscationEngine promptShieldObfuscationEngine(ObfuscationConfig config, StorageService promptShieldStorageService) {
        return new ObfuscationEngine(config, promptShieldStorageService);
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptShieldAdvisor promptShieldAdvisor(ObfuscationEngine engine) {
        PromptShieldProperties.AdvisorProperties advisorProps = properties.getAdvisor();
        PromptShieldProperties.InjectionProperties injectionProps = properties.getInjection();
        PromptShieldAdvisor.InjectionOptions injection = injectionProps.isEnabled()
                ? new PromptShieldAdvisor.InjectionOptions(
                        injectionProps.toGuard(engine.getTagPattern()),
                        injectionProps.isProtectOnInjection(),
                        injectionProps.isScanSystemMessages(),
                        injectionProps.isScanToolDefinitions())
                : null;
        return new PromptShieldAdvisor(
                engine,
                advisorProps.getOrder(),
                advisorProps.isRestoreOnResponse(),
                advisorProps.isInjectSystemPrompt(),
                advisorProps.getSystemPrompt(),
                advisorProps.isObfuscateSystemMessages(),
                advisorProps.isObfuscateToolResponses(),
                injection
        );
    }
}
