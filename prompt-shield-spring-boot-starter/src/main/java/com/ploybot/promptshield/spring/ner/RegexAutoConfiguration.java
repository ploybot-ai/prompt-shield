package com.ploybot.promptshield.spring.ner;

import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.ner.EntityDetector;
import com.ploybot.promptshield.ner.regex.RegexEntityDetector;
import com.ploybot.promptshield.spring.autoconfigure.ObfuscationAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = ObfuscationAutoConfiguration.class,
        afterName = "com.ploybot.promptshield.spring.ai.autoconfigure.PromptShieldAdvisorAutoConfiguration")
@ConditionalOnProperty(prefix = "prompt-shield.ner", name = "type", havingValue = "regex", matchIfMissing = true)
public class RegexAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(EntityDetector.class)
    @ConditionalOnBean(ObfuscationConfig.class)
    public EntityDetector regexEntityDetector(ObfuscationConfig config) {
        return new RegexEntityDetector(config);
    }
}
