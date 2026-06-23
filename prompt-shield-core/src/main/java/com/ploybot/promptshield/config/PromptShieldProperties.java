package com.ploybot.promptshield.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "prompt-shield")
public class PromptShieldProperties {

    private boolean enabled = true;
    private String hashAlgorithm = "SHA-256";
    private int hashLength = 6;
    private String storageType = "memory";
    private String redactedPrefix = "REDACTED";
    private String tagSeparator = "#";
    private String tagOpen = "~";
    private String tagClose = "~";
    private final Map<String, CustomTypeProperties> customTypes = new HashMap<>();
    private final AdvisorProperties advisor = new AdvisorProperties();
    private final ProxyProperties proxy = new ProxyProperties();
    private final StorageProperties storage = new StorageProperties();

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

    public String getStorageType() {
        return storageType;
    }

    public void setStorageType(String storageType) {
        this.storageType = storageType;
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

    public String getTagOpen() {
        return tagOpen;
    }

    public void setTagOpen(String tagOpen) {
        this.tagOpen = tagOpen;
    }

    public String getTagClose() {
        return tagClose;
    }

    public void setTagClose(String tagClose) {
        this.tagClose = tagClose;
    }

    public Map<String, CustomTypeProperties> getCustomTypes() {
        return customTypes;
    }

    public AdvisorProperties getAdvisor() {
        return advisor;
    }

    public ProxyProperties getProxy() {
        return proxy;
    }

    public StorageProperties getStorage() {
        return storage;
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

    public static class AdvisorProperties {
        private boolean enabled = true;
        private int order = 0;
        private boolean restoreOnResponse = true;
        private boolean injectSystemPrompt = true;
        private String systemPrompt;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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
    }

    public static class ProxyProperties {
        private boolean enabled = true;
        private String basePath = "/v1";
        private boolean injectSystemPrompt = true;
        private boolean restoreOnResponse = true;
        private final ProviderProperties provider = new ProviderProperties();

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

        public ProviderProperties getProvider() {
            return provider;
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
    }

    public static class StorageProperties {
        private String type = "memory";
        private final JpaProperties jpa = new JpaProperties();
        private final RedisProperties redis = new RedisProperties();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public JpaProperties getJpa() {
            return jpa;
        }

        public RedisProperties getRedis() {
            return redis;
        }

        public static class JpaProperties {
            private long ttlHours = 24;
            private boolean cleanupEnabled = true;

            public long getTtlHours() {
                return ttlHours;
            }

            public void setTtlHours(long ttlHours) {
                this.ttlHours = ttlHours;
            }

            public boolean isCleanupEnabled() {
                return cleanupEnabled;
            }

            public void setCleanupEnabled(boolean cleanupEnabled) {
                this.cleanupEnabled = cleanupEnabled;
            }
        }

        public static class RedisProperties {
            private long ttlHours = 24;

            public long getTtlHours() {
                return ttlHours;
            }

            public void setTtlHours(long ttlHours) {
                this.ttlHours = ttlHours;
            }
        }
    }
}
