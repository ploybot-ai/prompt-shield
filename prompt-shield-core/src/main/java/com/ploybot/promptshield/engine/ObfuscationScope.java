package com.ploybot.promptshield.engine;

import com.ploybot.promptshield.exception.ObfuscationException;
import com.ploybot.promptshield.model.ObfuscationTag;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A view of an {@link ObfuscationEngine} bound to a single scope. The scope id is an
 * opaque key chosen by the caller: a conversation id for per-conversation isolation,
 * a user id to share tags across all conversations of the same user, a tenant id, etc.
 *
 * <p>Tags created in a scope can only be restored through a scope with the same id,
 * which isolates scopes from each other and makes concurrent use safe.
 *
 * <p>Each instance remembers the mappings it created, so a short-lived scope (one
 * request, one method call) can release them with {@link #removeCreatedMappings()}
 * once its response has been restored.
 *
 * <p>Obtain instances via {@link ObfuscationEngine#forScope(String)}.
 */
public final class ObfuscationScope {

    private final ObfuscationEngine engine;
    private final String scopeId;
    private final Set<String> createdKeys = ConcurrentHashMap.newKeySet();

    ObfuscationScope(ObfuscationEngine engine, String scopeId) {
        this.engine = engine;
        this.scopeId = scopeId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public ObfuscationEngine getEngine() {
        return engine;
    }

    public String obfuscate(String text) {
        return engine.obfuscateScoped(text, this);
    }

    public String obfuscate(String text, String typeName) {
        return engine.obfuscateScoped(text, List.of(typeName), this);
    }

    /** Obfuscates only the given registered types. */
    public String obfuscate(String text, Collection<String> typeNames) {
        return engine.obfuscateScoped(text, typeNames, this);
    }

    /**
     * Obfuscates one already-identified value without applying a detection pattern.
     * This is intended for structured fields whose semantic type is known by the
     * caller, including application-specific types not registered in the engine.
     */
    public String obfuscateValue(String value, String typeName) {
        return engine.obfuscateValueScoped(value, typeName, this);
    }

    public String restore(String text) {
        return engine.restoreScoped(text, scopeId);
    }

    public boolean containsTags(String text) {
        return engine.containsTags(text);
    }

    public List<ObfuscationTag> extractTags(String text) {
        return engine.extractTagsScoped(text, scopeId);
    }

    /** Removes mappings referenced by tags in the supplied text. */
    public int removeMappings(String text) {
        return engine.removeMappingsScoped(text, scopeId);
    }

    /**
     * Removes every mapping this instance created. Mappings that already existed when
     * this instance obfuscated the same value (e.g. created by an earlier request of
     * the same conversation) are left untouched. Tags produced by this instance can no
     * longer be restored afterwards.
     *
     * @return number of mappings removed
     */
    public int removeCreatedMappings() {
        int removed = 0;
        for (String key : createdKeys) {
            if (createdKeys.remove(key)) {
                engine.getStorageService().remove(key);
                removed++;
            }
        }
        return removed;
    }

    /**
     * Obfuscates every string value of a JSON document; numbers and object keys are left
     * as they are. See {@link #obfuscateJson(String, boolean)}.
     */
    public String obfuscateJson(String json) {
        return engine.obfuscateJsonScoped(json, false, this);
    }

    /**
     * Obfuscates a JSON document. With {@code includeNumbersAndKeys}, numeric values and
     * object keys are obfuscated too: a phone number stored as a JSON number or a map
     * keyed by email is then protected, and numbers that contain sensitive data become
     * strings so the document stays valid. It is off by default because broad patterns
     * such as {@code CODIGO_POSTAL} would also turn plain amounts or ids into tags.
     */
    public String obfuscateJson(String json, boolean includeNumbersAndKeys) {
        return engine.obfuscateJsonScoped(json, includeNumbersAndKeys, this);
    }

    public String restoreJson(String json) {
        return engine.restoreJsonScoped(json, scopeId);
    }

    /**
     * Obfuscates a payload that may be JSON, such as tool call arguments or tool
     * results: a JSON object or array is handled like {@link #obfuscateJson(String)}
     * so it stays valid, anything else is obfuscated as plain text.
     */
    public String obfuscateJsonOrText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return obfuscateJson(text);
            } catch (ObfuscationException notJson) {
                // fall through to plain text
            }
        }
        return obfuscate(text);
    }

    /**
     * Serializes the object to JSON and obfuscates it including numbers and keys (see
     * {@link #obfuscateJson(String, boolean)}); {@link #restoreObject} converts
     * restored numeric strings back to the field types.
     */
    public String obfuscateObject(Object object) {
        return engine.obfuscateObjectScoped(object, this);
    }

    /** Restores an obfuscated JSON document and deserializes it. */
    public <T> T restoreObject(String json, Class<T> type) {
        return engine.restoreObjectScoped(json, type, scopeId);
    }

    /**
     * Creates a restorer for a streamed response of this scope. One instance per
     * stream; it is stateful and not thread-safe.
     */
    public StreamingRestorer streamingRestorer() {
        return new StreamingRestorer(this);
    }

    void recordCreated(String key) {
        createdKeys.add(key);
    }
}
