package com.ploybot.promptshield.proxy.autoconfigure;

import com.ploybot.promptshield.config.PromptShieldProperties;
import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.proxy.client.AiProviderClient;
import com.ploybot.promptshield.proxy.controller.ProxyAuthorization;
import com.ploybot.promptshield.proxy.controller.ProxyController;
import com.ploybot.promptshield.proxy.service.ProxyService;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import com.ploybot.promptshield.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

/**
 * OpenAI-compatible proxy endpoints. Only active with {@code prompt-shield.proxy.enabled=true},
 * so adding this module to an application never exposes them by accident.
 */
@AutoConfiguration
@ConditionalOnClass(ObfuscationEngine.class)
@ConditionalOnProperty(prefix = "prompt-shield.proxy", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(PromptShieldProperties.class)
public class ApiProxyAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(ApiProxyAutoConfiguration.class);

    private final PromptShieldProperties properties;

    public ApiProxyAutoConfiguration(PromptShieldProperties properties) {
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
    public AiProviderClient aiProviderClient() {
        return new AiProviderClient(properties.getProxy().getProvider().getBaseUrl());
    }

    @Bean
    @ConditionalOnMissingBean
    public ProxyService proxyService(AiProviderClient aiClient, ObfuscationEngine engine) {
        PromptShieldProperties.ProxyProperties proxyProps = properties.getProxy();
        PromptShieldProperties.InjectionProperties injectionProps = properties.getInjection();
        return new ProxyService(
                aiClient,
                engine,
                proxyProps.isInjectSystemPrompt(),
                proxyProps.isRestoreOnResponse(),
                injectionProps.isEnabled() ? injectionProps.toGuard(engine.getTagPattern()) : null,
                injectionProps.isProtectOnInjection(),
                new java.util.HashSet<>(injectionProps.getRestoreToolArgumentsFor())
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public ProxyAuthorization proxyAuthorization() {
        PromptShieldProperties.ProxyProperties proxyProps = properties.getProxy();
        ProxyAuthorization authorization = new ProxyAuthorization(
                proxyProps.getClientTokens(),
                proxyProps.getProvider().getApiKey(),
                proxyProps.isAllowUnauthenticated());
        if (authorization.exposesProviderKeyWithoutAuthentication()) {
            logger.warn("Prompt Shield proxy: anyone who can reach it can use the provider API key "
                    + "(allow-unauthenticated=true without client-tokens)");
        }
        return authorization;
    }

    @Bean
    @ConditionalOnMissingBean
    public ProxyController proxyController(ProxyService proxyService, ProxyAuthorization proxyAuthorization) {
        return new ProxyController(proxyService, proxyAuthorization);
    }
}
