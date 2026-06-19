package com.ploybot.promptshield.spring.ai.autoconfigure;

import com.ploybot.promptshield.spring.ai.advisor.PromptShieldAdvisor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "prompt-shield.advisor")
public class PromptShieldAdvisorProperties {

    private boolean enabled = true;
    private String hashAlgorithm = "SHA-256";
    private int hashLength = 6;
    private int order = 0;
    private boolean restoreOnResponse = true;
    private boolean injectSystemPrompt = true;
    private String systemPrompt = PromptShieldAdvisor.SYSTEM_PROMPT_EN;
    private final Map<String, CustomTypeProperties> customTypes = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    public void setHashAlgorithm(String hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }

    public int getHashLength() {
        return hashLength;
    }

    public void setHashLength(int hashLength) {
        this.hashLength = hashLength;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public boolean isRestoreOnResponse() {
        return restoreOnResponse;
    }

    public void setRestoreOnResponse(boolean restoreOnResponse) {
        this.restoreOnResponse = restoreOnResponse;
    }

    public boolean isInjectSystemPrompt() {
        return injectSystemPrompt;
    }

    public void setInjectSystemPrompt(boolean injectSystemPrompt) {
        this.injectSystemPrompt = injectSystemPrompt;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public Map<String, CustomTypeProperties> getCustomTypes() {
        return customTypes;
    }

    public static class CustomTypeProperties {
        private String pattern;

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
    }
}
