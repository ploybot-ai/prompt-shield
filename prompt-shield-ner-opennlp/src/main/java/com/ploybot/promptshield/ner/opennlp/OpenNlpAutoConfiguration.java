package com.ploybot.promptshield.ner.opennlp;

import com.ploybot.promptshield.ner.EntityDetector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = "opennlp.tools.namefind.NameFinderME")
@ConditionalOnProperty(prefix = "prompt-shield.ner", name = "type", havingValue = "opennlp", matchIfMissing = false)
public class OpenNlpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EntityDetector openNlpEntityDetector() {
        return new OpenNlpEntityDetector();
    }
}
