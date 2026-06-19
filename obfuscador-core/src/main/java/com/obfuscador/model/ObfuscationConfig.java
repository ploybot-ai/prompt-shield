package com.obfuscador.model;

import java.util.HashMap;
import java.util.Map;

public class ObfuscationConfig {

    private String hashAlgorithm = "SHA-256";
    private int hashLength = 6;
    private String storageType = "memory";
    private boolean enabled = true;
    private final Map<String, CustomTypeConfig> customTypes = new HashMap<>();

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, CustomTypeConfig> getCustomTypes() {
        return customTypes;
    }

    public void addCustomType(String name, String pattern) {
        this.customTypes.put(name, new CustomTypeConfig(pattern));
    }

    public static class CustomTypeConfig {
        private String pattern;

        public CustomTypeConfig(String pattern) {
            this.pattern = pattern;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }
    }
}
