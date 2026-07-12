package com.ploybot.promptshield.proxy.autoconfigure;

import com.ploybot.promptshield.config.PromptShieldProperties;
import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.proxy.client.AiProviderClient;
import com.ploybot.promptshield.proxy.controller.ProxyController;
import com.ploybot.promptshield.proxy.service.ProxyService;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import com.ploybot.promptshield.storage.StorageService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(ObfuscationEngine.class)
@ConditionalOnProperty(prefix = "prompt-shield.proxy", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PromptShieldProperties.class)
public class ApiProxyAutoConfiguration {

    private final PromptShieldProperties properties;

    public ApiProxyAutoConfiguration(PromptShieldProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public ObfuscationConfig promptShieldObfuscationConfig() {
        ObfuscationConfig config = new ObfuscationConfig();
        config.setHashAlgorithm(properties.getHashAlgorithm());
        config.setHashLength(properties.getHashLength());
        config.setRedactedPrefix(properties.getRedactedPrefix());
        config.setTagSeparator(properties.getTagSeparator());
        config.setTagOpen(properties.getTagOpen());
        config.setTagClose(properties.getTagClose());
        config.setStorageType(properties.getStorage().getType());
        config.setEnabled(properties.isEnabled());
        config.setServiceKeysEnabled(properties.isServiceKeysEnabled());
        config.setChecksumValidationEnabled(properties.isChecksumValidationEnabled());
        config.setHmacSecret(properties.getHmacSecret());

        for (var entry : properties.getCustomTypes().entrySet()) {
            config.addCustomType(entry.getKey(), entry.getValue().getPattern());
        }
        return config;
    }

    @Bean
    @ConditionalOnMissingBean(StorageService.class)
    @ConditionalOnProperty(prefix = "prompt-shield.storage", name = "type", havingValue = "memory", matchIfMissing = true)
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
    public AiProviderClient aiProviderClient() {
        return new AiProviderClient(
                properties.getProxy().getProvider().getBaseUrl(),
                properties.getProxy().getProvider().getApiKey()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ProxyService proxyService(AiProviderClient aiClient, ObfuscationEngine engine) {
        PromptShieldProperties.ProxyProperties proxyProps = properties.getProxy();
        return new ProxyService(
                aiClient,
                engine,
                proxyProps.isInjectSystemPrompt(),
                proxyProps.isRestoreOnResponse()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ProxyController proxyController(ProxyService proxyService) {
        return new ProxyController(proxyService);
    }
}
