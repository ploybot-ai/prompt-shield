package com.ploybot.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class DataTypeRegistry {

    private final Map<String, Pattern> registeredTypes;

    public DataTypeRegistry() {
        this.registeredTypes = new LinkedHashMap<>();
        registerBuiltInTypes();
    }

    private void registerBuiltInTypes() {
        for (SensitiveDataType type : SensitiveDataType.values()) {
            registeredTypes.put(type.getTypeName(), Pattern.compile(type.getPattern()));
        }
    }

    public void register(String typeName, String pattern) {
        registeredTypes.put(typeName, Pattern.compile(pattern));
    }

    public void unregister(String typeName) {
        registeredTypes.remove(typeName);
    }

    public Pattern getPattern(String typeName) {
        return registeredTypes.get(typeName);
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
