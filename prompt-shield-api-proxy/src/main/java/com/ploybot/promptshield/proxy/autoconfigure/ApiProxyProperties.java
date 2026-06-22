package com.ploybot.promptshield.proxy.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "prompt-shield.proxy")
public class ApiProxyProperties {

    private boolean enabled = true;
    private String basePath = "/v1";
    private ProviderProperties provider = new ProviderProperties();
    private ObfuscationProperties obfuscation = new ObfuscationProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public ProviderProperties getProvider() {
        return provider;
    }

    public void setProvider(ProviderProperties provider) {
        this.provider = provider;
    }

    public ObfuscationProperties getObfuscation() {
        return obfuscation;
    }

    public void setObfuscation(ObfuscationProperties obfuscation) {
        this.obfuscation = obfuscation;
    }

    public static class ProviderProperties {
        private String baseUrl = "https://api.openai.com";
        private String apiKey;
        private String model = "gpt-4o-mini";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class ObfuscationProperties {
        private boolean enabled = true;
        private boolean injectSystemPrompt = true;
        private boolean restoreOnResponse = true;
        private String hashAlgorithm = "SHA-256";
        private int hashLength = 6;
        private String redactedPrefix = "REDACTED";
        private String tagSeparator = "#";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isInjectSystemPrompt() {
            return injectSystemPrompt;
        }

        public void setInjectSystemPrompt(boolean injectSystemPrompt) {
            this.injectSystemPrompt = injectSystemPrompt;
        }

        public boolean isRestoreOnResponse() {
            return restoreOnResponse;
        }

        public void setRestoreOnResponse(boolean restoreOnResponse) {
            this.restoreOnResponse = restoreOnResponse;
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

        public String getRedactedPrefix() {
            return redactedPrefix;
        }

        public void setRedactedPrefix(String redactedPrefix) {
            this.redactedPrefix = redactedPrefix;
        }

        public String getTagSeparator() {
            return tagSeparator;
        }

        public void setTagSeparator(String tagSeparator) {
            this.tagSeparator = tagSeparator;
        }
    }
}
