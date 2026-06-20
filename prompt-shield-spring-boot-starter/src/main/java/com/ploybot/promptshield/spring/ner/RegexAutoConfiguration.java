package com.ploybot.promptshield.spring.ner;

import com.ploybot.promptshield.model.ObfuscationConfig;
import com.ploybot.promptshield.ner.EntityDetector;
import com.ploybot.promptshield.ner.regex.RegexEntityDetector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnMissingBean(EntityDetector.class)
@ConditionalOnProperty(prefix = "prompt-shield.ner", name = "type", havingValue = "regex", matchIfMissing = true)
public class RegexAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EntityDetector regexEntityDetector(ObfuscationConfig config) {
        return new RegexEntityDetector(config);
    }
}
