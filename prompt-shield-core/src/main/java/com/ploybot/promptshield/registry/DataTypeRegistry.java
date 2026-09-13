package com.ploybot.promptshield.registry;

import com.ploybot.promptshield.ner.DetectedEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataTypeRegistry {

    /**
     * Valid type names. They appear verbatim inside tags, so they must be matchable by
     * the tag pattern: an uppercase letter followed by uppercase letters, digits or
     * underscores.
     */
    public static final String TYPE_NAME_REGEX = "[A-Z][A-Z0-9_]*";

    private static final Pattern TYPE_NAME = Pattern.compile(TYPE_NAME_REGEX);

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

    public static boolean isValidTypeName(String typeName) {
        return typeName != null && TYPE_NAME.matcher(typeName).matches();
    }

    public void register(String typeName, String pattern) {
        register(typeName, pattern, null);
    }

    /**
     * Registers (or replaces) a type. A replaced type keeps its original priority.
     *
     * @throws IllegalArgumentException if the name does not match {@link #TYPE_NAME_REGEX}
     *                                  or the pattern is missing
     */
    public void register(String typeName, String pattern, Predicate<String> validator) {
        if (!isValidTypeName(typeName)) {
            throw new IllegalArgumentException("Invalid type name '" + typeName + "': it must match "
                    + TYPE_NAME_REGEX + " (uppercase letters, digits and underscores, starting with a letter)");
        }
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("Pattern for type " + typeName + " must not be empty");
        }
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

    /** Detects all registered types. See {@link #detect(String, Collection, List)}. */
    public List<DetectedEntity> detect(String text) {
        return detect(text, registeredTypes.keySet(), List.of());
    }

    /**
     * Detects the given types on the original text in a single pass, so a pattern can
     * never match inside a value already claimed by a higher-priority type.
     *
     * <p>Types are evaluated in registration order; a match overlapping an accepted
     * match or one of the {@code protectedRanges} (each {@code [start, end)}) is
     * discarded. Matches rejected by the type's validator are discarded as well.
     *
     * @return non-overlapping entities sorted by start offset
     */
    public List<DetectedEntity> detect(String text, Collection<String> typeNames, List<int[]> protectedRanges) {
        List<DetectedEntity> accepted = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return accepted;
        }
        List<int[]> taken = new ArrayList<>(protectedRanges);

        for (String typeName : typeNames) {
            Pattern pattern = registeredTypes.get(typeName);
            if (pattern == null) {
                continue;
            }
            Predicate<String> validator = validators.get(typeName);
            Matcher matcher = pattern.matcher(text);
            int from = 0;
            while (from < text.length() && matcher.find(from)) {
                int start = matcher.start();
                int end = matcher.end();
                if (end == start) {
                    from = start + 1;
                    continue;
                }
                int[] overlap = firstOverlap(taken, start, end);
                if (overlap != null) {
                    // A match starting inside a taken range can only resume after it
                    from = overlap[0] <= start ? overlap[1] : start + 1;
                    continue;
                }
                String value = matcher.group();
                if (validator != null && !validator.test(value)) {
                    from = end;
                    continue;
                }
                accepted.add(new DetectedEntity(typeName, value, start, end));
                taken.add(new int[]{start, end});
                from = end;
            }
        }

        accepted.sort(Comparator.comparingInt(DetectedEntity::getStart));
        return accepted;
    }

    private static int[] firstOverlap(List<int[]> ranges, int start, int end) {
        for (int[] range : ranges) {
            if (start < range[1] && range[0] < end) {
                return range;
            }
        }
        return null;
    }
}
