package com.ploybot.promptshield.storage.jpa;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.engine.ObfuscationScope;
import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.model.ObfuscationTag;
import com.ploybot.promptshield.storage.StorageService;
import com.ploybot.promptshield.storage.jpa.app.HostApplication;
import com.ploybot.promptshield.storage.jpa.app.Invoice;
import com.ploybot.promptshield.storage.jpa.app.InvoiceRepository;
import com.ploybot.promptshield.storage.jpa.emptyapp.EmptyApplication;
import com.ploybot.promptshield.storage.jpa.entity.ObfuscationTagEntity;
import com.ploybot.promptshield.storage.jpa.repository.ObfuscationTagRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JpaStorageIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class,
                    TransactionAutoConfiguration.class,
                    DataJpaRepositoriesAutoConfiguration.class,
                    JpaStorageAutoConfiguration.class))
            .withUserConfiguration(EmptyApplication.class)
            .withPropertyValues(
                    "prompt-shield.storage.type=jpa",
                    "spring.datasource.url=jdbc:h2:mem:ps-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");

    @Test
    void createsTheSchemaThatMatchesTheEntityAndHandlesLongScopeIds() {
        runner.withPropertyValues("spring.jpa.hibernate.ddl-auto=validate").run(context -> {
            assertThat(context).hasNotFailed();
            ObfuscationEngine engine = new ObfuscationEngine(new ObfuscationConfig(), context.getBean(StorageService.class));
            ObfuscationScope scope = engine.forScope("tenant-" + UUID.randomUUID() + "-conversation-" + UUID.randomUUID());

            String obfuscated = scope.obfuscate("DNI 12345678Z, email juan@example.com");

            assertThat(obfuscated).doesNotContain("12345678Z");
            assertThat(scope.restore(obfuscated)).isEqualTo("DNI 12345678Z, email juan@example.com");
            assertThat(scope.extractTags(obfuscated)).extracting(ObfuscationTag::getHash)
                    .allMatch(hash -> obfuscated.contains("#" + hash + "~"), "tags must expose the id, not the storage key");
        });
    }

    @Test
    void applicationEntitiesAndRepositoriesKeepWorking() {
        runner.withUserConfiguration(HostApplication.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=create-drop")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(InvoiceRepository.class);
                    InvoiceRepository invoices = context.getBean(InvoiceRepository.class);
                    invoices.save(new Invoice(1L, "ACME"));
                    assertThat(invoices.findById(1L)).map(Invoice::getCustomer).contains("ACME");

                    StorageService storage = context.getBean(StorageService.class);
                    storage.store("scope:abc123", new ObfuscationTag("DNI", "abc123", "12345678Z"));
                    assertThat(storage.retrieve("scope:abc123")).map(ObfuscationTag::getOriginalValue).contains("12345678Z");
                });
    }

    @Test
    void storeIfAbsentKeepsTheFirstMapping() {
        runner.run(context -> {
            StorageService storage = context.getBean(StorageService.class);

            assertThat(storage.storeIfAbsent("s:1", new ObfuscationTag("DNI", "1", "12345678Z"))).isEmpty();
            assertThat(storage.storeIfAbsent("s:1", new ObfuscationTag("DNI", "1", "87654321X")))
                    .map(ObfuscationTag::getOriginalValue).contains("12345678Z");
        });
    }

    @Test
    void expiredMappingsAreIgnoredRefreshedOnUseAndCleanedUp() {
        runner.run(context -> {
            MutableClock clock = new MutableClock();
            JpaStorageService storage = new JpaStorageService(repository(context), 24,
                    new TransactionTemplate(context.getBean(PlatformTransactionManager.class)), clock);

            storage.store("s:old", new ObfuscationTag("DNI", "old", "12345678Z"));
            storage.store("s:used", new ObfuscationTag("DNI", "used", "87654321X"));
            clock.advance(Duration.ofHours(20));
            storage.refresh("s:used");
            clock.advance(Duration.ofHours(5));

            assertThat(storage.retrieve("s:old")).as("past the TTL").isEmpty();
            assertThat(storage.retrieve("s:used")).as("refreshed 5 hours ago").isPresent();
            assertThat(storage.cleanupExpired()).isEqualTo(1);
            assertThat(storage.size()).isEqualTo(1);
        });
    }

    @Test
    void rowsWrittenWithTheLegacyRawKeyAreStillFound() {
        runner.run(context -> {
            ObfuscationTagRepository repository = repository(context);
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status ->
                    repository.save(new ObfuscationTagEntity("conv-1:abc123", "DNI", "12345678Z")));

            StorageService storage = context.getBean(StorageService.class);
            assertThat(storage.retrieve("conv-1:abc123")).map(ObfuscationTag::getOriginalValue).contains("12345678Z");
        });
    }

    @Test
    void cleanupRunsByDefaultAndCanBeDisabled() {
        runner.run(context -> assertThat(context).hasSingleBean(JpaStorageCleanup.class));
        runner.withPropertyValues("prompt-shield.storage.jpa.cleanup-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(JpaStorageCleanup.class));
    }

    private static ObfuscationTagRepository repository(ApplicationContext context) {
        return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(
                context.getBean(EntityManagerFactory.class))).getRepository(ObfuscationTagRepository.class);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
