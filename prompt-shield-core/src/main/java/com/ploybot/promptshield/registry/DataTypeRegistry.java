package com.ploybot.promptshield.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class DataTypeRegistry {

    private final Map<String, Pattern> registeredTypes;
    private final Map<String, Predicate<String>> validators;

    public DataTypeRegistry() {
        this(true);
    }

    public DataTypeRegistry(boolean serviceKeysEnabled) {
        this(serviceKeysEnabled, true);
    }

    public DataTypeRegistry(boolean serviceKeysEnabled, boolean checksumValidationEnabled) {
        this.registeredTypes = new LinkedHashMap<>();
        this.validators = new LinkedHashMap<>();
        registerBuiltInTypes(serviceKeysEnabled, checksumValidationEnabled);
    }

    private void registerBuiltInTypes(boolean serviceKeysEnabled, boolean checksumValidationEnabled) {
        for (SensitiveDataType type : SensitiveDataType.values()) {
            if (!serviceKeysEnabled && type.isServiceKey()) {
                continue;
            }
            Predicate<String> validator = checksumValidationEnabled ? type.getValidator() : null;
            register(type.getTypeName(), type.getPattern(), validator);
        }
    }

    public void register(String typeName, String pattern) {
        register(typeName, pattern, null);
    }

    public void register(String typeName, String pattern, Predicate<String> validator) {
        registeredTypes.put(typeName, Pattern.compile(pattern));
        if (validator != null) {
            validators.put(typeName, validator);
        } else {
            validators.remove(typeName);
        }
    }

    public void unregister(String typeName) {
        registeredTypes.remove(typeName);
        validators.remove(typeName);
    }

    public Pattern getPattern(String typeName) {
        return registeredTypes.get(typeName);
    }

    /**
     * Returns the checksum validator for the type, or {@code null} if the type has
     * none (a regex match is then accepted as-is).
     */
    public Predicate<String> getValidator(String typeName) {
        return validators.get(typeName);
    }

    public boolean isRegistered(String typeName) {
        return registeredTypes.containsKey(typeName);
    }

    public Collection<String> getRegisteredTypeNames() {
        return Collections.unmodifiableSet(registeredTypes.keySet());
    }

    public Map<String, Pattern> getAllPatterns() {
        return Collections.unmodifiableMap(registeredTypes);
    }
}
