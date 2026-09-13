package com.ploybot.promptshield.spring.autoconfigure;

import com.ploybot.promptshield.engine.ObfuscationEngine;
import com.ploybot.promptshield.ner.DetectedEntity;
import com.ploybot.promptshield.ner.EntityDetector;
import com.ploybot.promptshield.spring.annotation.Obfuscate;
import com.ploybot.promptshield.spring.interceptor.ObfuscationInterceptor;
import com.ploybot.promptshield.spring.ner.RegexAutoConfiguration;
import com.ploybot.promptshield.storage.InMemoryStorageService;
import com.ploybot.promptshield.storage.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ObfuscationAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AopAutoConfiguration.class, ObfuscationAutoConfiguration.class, RegexAutoConfiguration.class))
            .withUserConfiguration(Services.class);

    @Test
    void obfuscateAnnotationOnAMethodIsApplied() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ObfuscationInterceptor.class);
            MethodLevelService service = context.getBean(MethodLevelService.class);

            service.send("Mi DNI es 12345678Z");

            assertThat(service.received()).doesNotContain("12345678Z").contains("~REDACTED:DNI#");
        });
    }

    @Test
    void obfuscateAnnotationOnAClassOrParameterIsApplied() {
        runner.run(context -> {
            ClassLevelService classLevel = context.getBean(ClassLevelService.class);
            classLevel.send("email juan@example.com");
            assertThat(classLevel.received()).doesNotContain("juan@example.com");

            ParameterLevelService parameterLevel = context.getBean(ParameterLevelService.class);
            parameterLevel.send("DNI 12345678Z", "DNI 12345678Z");
            assertThat(parameterLevel.obfuscated()).doesNotContain("12345678Z");
            assertThat(parameterLevel.untouched()).isEqualTo("DNI 12345678Z");
        });
    }

    @Test
    void restoreUsesAPerCallScopeAndReleasesIt() {
        runner.run(context -> {
            RestoringService service = context.getBean(RestoringService.class);
            InMemoryStorageService storage = (InMemoryStorageService) context.getBean(StorageService.class);

            String result = service.echo("DNI 12345678Z y tel 612345678");

            assertThat(service.received()).doesNotContain("12345678Z").doesNotContain("612345678");
            assertThat(result).isEqualTo("Respuesta: DNI 12345678Z y tel 612345678");
            assertThat(storage.size()).isZero();
        });
    }

    @Test
    void typesRestrictWhatIsObfuscated() {
        runner.run(context -> {
            MethodLevelService service = context.getBean(MethodLevelService.class);
            service.sendOnlyDni("DNI 12345678Z, email juan@example.com");
            assertThat(service.received()).doesNotContain("12345678Z").contains("juan@example.com");
        });
    }

    @Test
    void customStorageBeanWithAnyNameIsUsed() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ObfuscationAutoConfiguration.class))
                .withBean("myStorage", StorageService.class, InMemoryStorageService::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ObfuscationEngine.class).getStorageService())
                            .isSameAs(context.getBean("myStorage"));
                });
    }

    @Test
    void customTypesBindWithBracketNotationAndKeepDeclarationOrder() {
        runner.withPropertyValues(
                        "prompt-shield.custom-types.[MATRICULA_ES].pattern=\\d{4}[A-Z]{3}",
                        "prompt-shield.custom-types.[REFERENCIA].pattern=REF-\\d{6}")
                .run(context -> {
                    ObfuscationEngine engine = context.getBean(ObfuscationEngine.class);
                    assertThat(engine.getRegistry().getRegisteredTypeNames()).endsWith("MATRICULA_ES", "REFERENCIA");
                    String obfuscated = engine.ofuscar("coche 1234BCD ref REF-123456");
                    assertThat(engine.restaurar(obfuscated)).isEqualTo("coche 1234BCD ref REF-123456");
                });
    }

    @Test
    void regexEntityDetectorIsRegistered() {
        runner.run(context -> {
            EntityDetector detector = context.getBean(EntityDetector.class);
            assertThat(detector.detect("DNI 12345678Z")).extracting(DetectedEntity::getType).containsExactly("DNI");
        });
    }

    @Test
    void annotationSupportCanBeDisabled() {
        runner.withPropertyValues("prompt-shield.annotation.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ObfuscationInterceptor.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class Services {
        @Bean
        MethodLevelService methodLevelService() {
            return new MethodLevelService();
        }

        @Bean
        ClassLevelService classLevelService() {
            return new ClassLevelService();
        }

        @Bean
        ParameterLevelService parameterLevelService() {
            return new ParameterLevelService();
        }

        @Bean
        RestoringService restoringService() {
            return new RestoringService();
        }
    }

    static class MethodLevelService {
        private final AtomicReference<String> received = new AtomicReference<>();

        public String received() {
            return received.get();
        }

        @Obfuscate
        public void send(String prompt) {
            received.set(prompt);
        }

        @Obfuscate(types = "DNI")
        public void sendOnlyDni(String prompt) {
            received.set(prompt);
        }
    }

    @Obfuscate
    static class ClassLevelService {
        private final AtomicReference<String> received = new AtomicReference<>();

        public String received() {
            return received.get();
        }

        public void send(String prompt) {
            received.set(prompt);
        }
    }

    static class ParameterLevelService {
        private final AtomicReference<String> obfuscated = new AtomicReference<>();
        private final AtomicReference<String> untouched = new AtomicReference<>();

        public String obfuscated() {
            return obfuscated.get();
        }

        public String untouched() {
            return untouched.get();
        }

        public void send(@Obfuscate String prompt, String other) {
            obfuscated.set(prompt);
            untouched.set(other);
        }
    }

    static class RestoringService {
        private final AtomicReference<String> received = new AtomicReference<>();

        public String received() {
            return received.get();
        }

        @Obfuscate(restore = true)
        public String echo(String prompt) {
            received.set(prompt);
            return "Respuesta: " + prompt;
        }
    }
}
