package com.ploybot.promptshield.engine;

import com.ploybot.promptshield.model.ObfuscationTag;

import java.util.List;

/**
 * A view of an {@link ObfuscationEngine} bound to a single scope. The scope id is an
 * opaque key chosen by the caller: a conversation id for per-conversation isolation,
 * a user id to share tags across all conversations of the same user, a tenant id, etc.
 *
 * <p>Tags created in a scope can only be restored through a scope with the same id,
 * which isolates scopes from each other and makes concurrent use safe: no shared
 * mutable state is involved.
 *
 * <p>Obtain instances via {@link ObfuscationEngine#forScope(String)}.
 */
public final class ObfuscationScope {

    private final ObfuscationEngine engine;
    private final String scopeId;

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
        return engine.obfuscateScoped(text, scopeId);
    }

    public String obfuscate(String text, String typeName) {
        return engine.obfuscateScoped(text, typeName, scopeId);
    }

    /**
     * Obfuscates one already-identified value without applying a detection pattern.
     * This is intended for structured fields whose semantic type is known by the
     * caller, including application-specific types not registered in the engine.
     */
    public String obfuscateValue(String value, String typeName) {
        return engine.obfuscateValueScoped(value, typeName, scopeId);
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

    public String obfuscateJson(String json) {
        return engine.obfuscateJsonScoped(json, scopeId);
    }

    public String restoreJson(String json) {
        return engine.restoreJsonScoped(json, scopeId);
    }

    /**
     * Creates a restorer for a streamed response of this scope. One instance per
     * stream; it is stateful and not thread-safe.
     */
    public StreamingRestorer streamingRestorer() {
        return new StreamingRestorer(this);
    }
}
