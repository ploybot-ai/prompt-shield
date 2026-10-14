package com.ploybot.promptshield.config;

import com.ploybot.promptshield.injection.ContentSource;
import com.ploybot.promptshield.injection.HeuristicInjectionDetector;
import com.ploybot.promptshield.injection.InjectionAction;
import com.ploybot.promptshield.injection.InjectionGuard;
import com.ploybot.promptshield.model.ObfuscationConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "prompt-shield")
public class PromptShieldProperties {

    private boolean enabled = true;
    private String hashAlgorithm = "SHA-256";
    private int hashLength = 6;
    private String redactedPrefix = "REDACTED";
    private String tagSeparator = "#";
    private String tagOpen = "~";
    private String tagClose = "~";
    private boolean serviceKeysEnabled = true;
    private boolean checksumValidationEnabled = true;
    private String hmacSecret;
    /**
     * Custom detectable types, evaluated after the built-in ones in declaration order.
     * Names must match {@code [A-Z][A-Z0-9_]*}; write names containing underscores
     * with bracket notation ({@code "[N_CUENTA]"}) so the binder keeps them.
     */
    private final Map<String, CustomTypeProperties> customTypes = new LinkedHashMap<>();
    private final AdvisorProperties advisor = new AdvisorProperties();
    private final InjectionProperties injection = new InjectionProperties();
    private final ProxyProperties proxy = new ProxyProperties();
    private final StorageProperties storage = new StorageProperties();

    /** Builds the engine configuration from these properties. */
    public ObfuscationConfig toObfuscationConfig() {
        ObfuscationConfig config = new ObfuscationConfig();
        config.setHashAlgorithm(hashAlgorithm);
        config.setHashLength(hashLength);
        config.setStorageType(storage.getType());
        config.setEnabled(enabled);
        config.setRedactedPrefix(redactedPrefix);
        config.setTagSeparator(tagSeparator);
        config.setTagOpen(tagOpen);
        config.setTagClose(tagClose);
        config.setServiceKeysEnabled(serviceKeysEnabled);
        config.setChecksumValidationEnabled(checksumValidationEnabled);
        config.setHmacSecret(hmacSecret);
        for (Map.Entry<String, CustomTypeProperties> entry : customTypes.entrySet()) {
            config.addCustomType(entry.getKey(), entry.getValue().getPattern());
        }
        return config;
    }

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

    /**
     * @deprecated use {@code prompt-shield.storage.type} instead. Note that only the
     * nested property activates the Redis/JPA storage auto-configurations.
     */
    @Deprecated
    public String getStorageType() {
        return storage.getType();
    }

    /**
     * @deprecated use {@code prompt-shield.storage.type} instead.
     */
    @Deprecated
    public void setStorageType(String storageType) {
        storage.setType(storageType);
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

    public boolean isServiceKeysEnabled() {
        return serviceKeysEnabled;
    }

    public void setServiceKeysEnabled(boolean serviceKeysEnabled) {
        this.serviceKeysEnabled = serviceKeysEnabled;
    }

    public boolean isChecksumValidationEnabled() {
        return checksumValidationEnabled;
    }

    public void setChecksumValidationEnabled(boolean checksumValidationEnabled) {
        this.checksumValidationEnabled = checksumValidationEnabled;
    }

    /**
     * Secret key for the HMAC used to derive tag identifiers. If not set, a random
     * per-instance key is generated at startup (tags then cannot be restored by a
     * different instance or after a restart).
     */
    public String getHmacSecret() {
        return hmacSecret;
    }

    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret;
    }

    public Map<String, CustomTypeProperties> getCustomTypes() {
        return customTypes;
    }

    public InjectionProperties getInjection() {
        return injection;
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

    /**
     * Prompt-injection detection in the Spring AI advisor and the API proxy. The default
     * action only reports (logs a warning without the text, adds the verdicts to the
     * response context); see the README for the trade-offs of each action.
     */
    public static class InjectionProperties {
        private boolean enabled = true;
        /** What to do with flagged user messages: monitor or block. */
        private InjectionAction userAction = InjectionAction.MONITOR;
        /** What to do with flagged tool results and tool descriptions: monitor, block or quarantine. */
        private InjectionAction toolAction = InjectionAction.MONITOR;
        /** Score at or above which user input is flagged. */
        private double userThreshold = HeuristicInjectionDetector.DEFAULT_THRESHOLD;
        /**
         * Score at or above which tool results, tool descriptions and documents are flagged.
         * 0.35 catches more indirect injections (instructions that only target "your
         * response") at the cost of more false positives on text addressed to people.
         */
        private double toolThreshold = HeuristicInjectionDetector.DEFAULT_THRESHOLD;
        /** Also scan de-obfuscated views (Unicode tricks, base64, rot13...). */
        private boolean deobfuscation = true;
        /**
         * When untrusted content was flagged but let through (monitor), keep placeholders in
         * tool call arguments and inside URLs of the response instead of restoring the real
         * values, so an injection cannot use Prompt Shield to exfiltrate them.
         */
        private boolean protectOnInjection = true;
        /** Scan the application's system prompt too (off: it is trusted). */
        private boolean scanSystemMessages = false;
        /** Scan tool descriptions and schemas offered to the model (MCP tool poisoning). */
        private boolean scanToolDefinitions = true;
        /** Rule ids to disable, e.g. to silence a source of false positives. */
        private List<String> disabledRules = new ArrayList<>();
        /**
         * Tools whose call arguments get the original values restored. Empty (default)
         * means every tool. Listing only the tools that need personal data (send_invoice,
         * create_customer...) keeps placeholders in the arguments of every other tool
         * (http_get, post_comment...), so an injection that detection misses still cannot
         * route the data through them.
         */
        private List<String> restoreToolArgumentsFor = new ArrayList<>();

        public List<String> getRestoreToolArgumentsFor() {
            return restoreToolArgumentsFor;
        }

        public void setRestoreToolArgumentsFor(List<String> restoreToolArgumentsFor) {
            this.restoreToolArgumentsFor = restoreToolArgumentsFor;
        }

        /** Builds the detector, taking the placeholder format of the obfuscation engine. */
        public HeuristicInjectionDetector toDetector(Pattern placeholderPattern) {
            return HeuristicInjectionDetector.builder()
                    .threshold(ContentSource.USER, userThreshold)
                    .threshold(ContentSource.SYSTEM, userThreshold)
                    .threshold(ContentSource.DOCUMENT, toolThreshold)
                    .threshold(ContentSource.TOOL_RESULT, toolThreshold)
                    .threshold(ContentSource.TOOL_DEFINITION, toolThreshold)
                    .deobfuscation(deobfuscation)
                    .disableRules(disabledRules)
                    .placeholderPattern(placeholderPattern)
                    .build();
        }

        public InjectionGuard toGuard(Pattern placeholderPattern) {
            return new InjectionGuard(toDetector(placeholderPattern), userAction, toolAction);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public InjectionAction getUserAction() {
            return userAction;
        }

        public void setUserAction(InjectionAction userAction) {
            this.userAction = userAction;
        }

        public InjectionAction getToolAction() {
            return toolAction;
        }

        public void setToolAction(InjectionAction toolAction) {
            this.toolAction = toolAction;
        }

        public double getUserThreshold() {
            return userThreshold;
        }

        public void setUserThreshold(double userThreshold) {
            this.userThreshold = userThreshold;
        }

        public double getToolThreshold() {
            return toolThreshold;
        }

        public void setToolThreshold(double toolThreshold) {
            this.toolThreshold = toolThreshold;
        }

        public boolean isDeobfuscation() {
            return deobfuscation;
        }

        public void setDeobfuscation(boolean deobfuscation) {
            this.deobfuscation = deobfuscation;
        }

        public boolean isProtectOnInjection() {
            return protectOnInjection;
        }

        public void setProtectOnInjection(boolean protectOnInjection) {
            this.protectOnInjection = protectOnInjection;
        }

        public boolean isScanSystemMessages() {
            return scanSystemMessages;
        }

        public void setScanSystemMessages(boolean scanSystemMessages) {
            this.scanSystemMessages = scanSystemMessages;
        }

        public boolean isScanToolDefinitions() {
            return scanToolDefinitions;
        }

        public void setScanToolDefinitions(boolean scanToolDefinitions) {
            this.scanToolDefinitions = scanToolDefinitions;
        }

        public List<String> getDisabledRules() {
            return disabledRules;
        }

        public void setDisabledRules(List<String> disabledRules) {
            this.disabledRules = disabledRules;
        }
    }

    public static class AdvisorProperties {
        private boolean enabled = true;
        private int order = 0;
        private boolean restoreOnResponse = true;
        private boolean injectSystemPrompt = true;
        private String systemPrompt;
        /** Obfuscate the application's system prompt text (it often carries user context). */
        private boolean obfuscateSystemMessages = true;
        /** Obfuscate tool results before they reach the model. */
        private boolean obfuscateToolResponses = true;

        public boolean isObfuscateSystemMessages() {
            return obfuscateSystemMessages;
        }

        public void setObfuscateSystemMessages(boolean obfuscateSystemMessages) {
            this.obfuscateSystemMessages = obfuscateSystemMessages;
        }

        public boolean isObfuscateToolResponses() {
            return obfuscateToolResponses;
        }

        public void setObfuscateToolResponses(boolean obfuscateToolResponses) {
            this.obfuscateToolResponses = obfuscateToolResponses;
        }

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
        /**
         * Registers the OpenAI-compatible proxy endpoints. Off by default so adding the
         * module to an application never exposes them unintentionally.
         */
        private boolean enabled = false;
        private String basePath = "/v1";
        private boolean injectSystemPrompt = true;
        private boolean restoreOnResponse = true;
        /**
         * Tokens clients must send as {@code Authorization: Bearer <token>}. When set,
         * the provider API key is used upstream and client tokens are never forwarded.
         */
        private List<String> clientTokens = new ArrayList<>();
        /**
         * Lets requests without an Authorization header use the provider API key when no
         * client tokens are configured. Only enable it on a trusted, private network.
         */
        private boolean allowUnauthenticated = false;
        private final ProviderProperties provider = new ProviderProperties();

        public List<String> getClientTokens() {
            return clientTokens;
        }

        public void setClientTokens(List<String> clientTokens) {
            this.clientTokens = clientTokens;
        }

        public boolean isAllowUnauthenticated() {
            return allowUnauthenticated;
        }

        public void setAllowUnauthenticated(boolean allowUnauthenticated) {
            this.allowUnauthenticated = allowUnauthenticated;
        }

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
        private final MemoryProperties memory = new MemoryProperties();
        private final JpaProperties jpa = new JpaProperties();
        private final RedisProperties redis = new RedisProperties();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public MemoryProperties getMemory() {
            return memory;
        }

        public JpaProperties getJpa() {
            return jpa;
        }

        public RedisProperties getRedis() {
            return redis;
        }

        public static class MemoryProperties {
            /** Hours a mapping is kept since it was last used; 0 keeps mappings forever. */
            private long ttlHours = 24;

            public long getTtlHours() {
                return ttlHours;
            }

            public void setTtlHours(long ttlHours) {
                this.ttlHours = ttlHours;
            }
        }

        public enum SchemaInitialization {
            /** Create the table only on embedded databases (H2, HSQLDB, Derby). */
            EMBEDDED,
            ALWAYS,
            NEVER
        }

        public static class JpaProperties {
            /** Hours a mapping is kept since it was last used. */
            private long ttlHours = 24;
            /** Periodically deletes expired mappings. */
            private boolean cleanupEnabled = true;
            private long cleanupIntervalMinutes = 60;
            /** When to run {@code CREATE TABLE IF NOT EXISTS} for the mappings table. */
            private SchemaInitialization initializeSchema = SchemaInitialization.EMBEDDED;

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

            public long getCleanupIntervalMinutes() {
                return cleanupIntervalMinutes;
            }

            public void setCleanupIntervalMinutes(long cleanupIntervalMinutes) {
                this.cleanupIntervalMinutes = cleanupIntervalMinutes;
            }

            public SchemaInitialization getInitializeSchema() {
                return initializeSchema;
            }

            public void setInitializeSchema(SchemaInitialization initializeSchema) {
                this.initializeSchema = initializeSchema;
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
