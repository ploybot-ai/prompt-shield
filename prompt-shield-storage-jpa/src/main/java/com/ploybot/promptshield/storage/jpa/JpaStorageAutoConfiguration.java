package com.ploybot.promptshield.storage.jpa;

import com.ploybot.promptshield.config.PromptShieldProperties;
import com.ploybot.promptshield.storage.StorageService;
import com.ploybot.promptshield.storage.jpa.entity.ObfuscationTagEntity;
import com.ploybot.promptshield.storage.jpa.repository.ObfuscationTagRepository;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryBuilderCustomizer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.boot.sql.init.dependency.DatabaseInitializationDependencyConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.List;

/**
 * Stores obfuscation mappings in the application's JPA persistence unit.
 *
 * <p>It deliberately uses no {@code @EntityScan}, {@code @EnableJpaRepositories} or
 * component scanning, which would replace the application's own entity and repository
 * scanning: the entity is added to the persistence unit through a customizer and the
 * repository is created directly.
 */
@AutoConfiguration(
        afterName = "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        beforeName = {
                "com.ploybot.promptshield.spring.autoconfigure.ObfuscationAutoConfiguration",
                "com.ploybot.promptshield.spring.ai.autoconfigure.PromptShieldAdvisorAutoConfiguration",
                "com.ploybot.promptshield.proxy.autoconfigure.ApiProxyAutoConfiguration"
        })
@ConditionalOnClass({EntityManagerFactory.class, JpaRepositoryFactory.class, EntityManagerFactoryBuilderCustomizer.class})
@ConditionalOnProperty(prefix = "prompt-shield.storage", name = "type", havingValue = "jpa")
@EnableConfigurationProperties(PromptShieldProperties.class)
// Makes the entity manager factory wait for the schema initializer below
@Import(DatabaseInitializationDependencyConfigurer.class)
public class JpaStorageAutoConfiguration {

    static final String SCHEMA_LOCATION = "classpath:com/ploybot/promptshield/storage/jpa/schema.sql";

    @Bean
    public EntityManagerFactoryBuilderCustomizer promptShieldEntityCustomizer() {
        return builder -> builder.addPersistenceUnitPostProcessors(
                unit -> unit.addManagedClassName(ObfuscationTagEntity.class.getName()));
    }

    @Bean
    @ConditionalOnMissingBean(name = "promptShieldSchemaInitializer")
    @ConditionalOnBean(DataSource.class)
    public DataSourceScriptDatabaseInitializer promptShieldSchemaInitializer(DataSource dataSource, PromptShieldProperties properties) {
        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setSchemaLocations(List.of(SCHEMA_LOCATION));
        settings.setMode(DatabaseInitializationMode.valueOf(
                properties.getStorage().getJpa().getInitializeSchema().name()));
        return new DataSourceScriptDatabaseInitializer(dataSource, settings);
    }

    @Bean
    @ConditionalOnMissingBean(StorageService.class)
    @Qualifier("promptShieldStorageService")
    public JpaStorageService jpaStorageService(EntityManagerFactory entityManagerFactory,
                                               PlatformTransactionManager transactionManager,
                                               PromptShieldProperties properties) {
        ObfuscationTagRepository repository = new JpaRepositoryFactory(
                SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory))
                .getRepository(ObfuscationTagRepository.class);
        return new JpaStorageService(repository, properties.getStorage().getJpa().getTtlHours(), transactionManager);
    }

    @Bean
    @ConditionalOnBean(JpaStorageService.class)
    @ConditionalOnProperty(prefix = "prompt-shield.storage.jpa", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
    public JpaStorageCleanup promptShieldJpaStorageCleanup(JpaStorageService storageService, PromptShieldProperties properties) {
        return new JpaStorageCleanup(storageService, properties.getStorage().getJpa().getCleanupIntervalMinutes());
    }
}
