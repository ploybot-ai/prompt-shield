package com.ploybot.promptshield.storage.jpa;

import com.ploybot.promptshield.storage.jpa.repository.ObfuscationTagRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@AutoConfiguration
@ConditionalOnClass(ObfuscationTagRepository.class)
@ConditionalOnProperty(prefix = "obfuscador.storage", name = "type", havingValue = "jpa", matchIfMissing = false)
@EnableConfigurationProperties(JpaStorageProperties.class)
@EntityScan(basePackages = "com.ploybot.promptshield.storage.jpa.entity")
@EnableJpaRepositories(basePackages = "com.ploybot.promptshield.storage.jpa.repository")
@ComponentScan(basePackages = "com.ploybot.promptshield.storage.jpa")
public class JpaStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JpaStorageService jpaStorageService(
            ObfuscationTagRepository repository,
            JpaStorageProperties properties) {
        return new JpaStorageService(repository, properties.getTtlHours());
    }
}
