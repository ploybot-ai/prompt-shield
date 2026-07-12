package com.ploybot.promptshield.storage.redis;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.spring.autoconfigure.ObfuscationAutoConfiguration;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import com.ploybot.promptshield.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class RedisStorageAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataRedisAutoConfiguration.class,
                    ObfuscationAutoConfiguration.class,
                    RedisStorageAutoConfiguration.class));

    @Test
    void redisStorageIsTheOnlyStorageServiceWhenTypeIsRedis() {
        runner.withPropertyValues("prompt-shield.storage.type=redis")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(StorageService.class);
                    assertThat(ctx.getBean(StorageService.class)).isInstanceOf(RedisStorageService.class);
                    assertThat(ctx).hasSingleBean(ObfuscationEngine.class);
                });
    }

    @Test
    void inMemoryStorageIsUsedByDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(StorageService.class);
            assertThat(ctx.getBean(StorageService.class)).isInstanceOf(InMemoryStorageService.class);
        });
    }

    @Test
    void userDefinedStorageServiceWins() {
        runner.withPropertyValues("prompt-shield.storage.type=redis")
                .withBean("promptShieldStorageService", StorageService.class, InMemoryStorageService::new)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(StorageService.class);
                    assertThat(ctx.getBean(StorageService.class)).isInstanceOf(InMemoryStorageService.class);
                });
    }
}
