package com.ploybot.promptshield.storage;

import com.ploybot.promptshield.model.ObfuscationTag;
import java.util.Optional;

/**
 * Stores the mapping from a scoped tag key ({@code scopeId + ":" + id}) to the
 * original value.
 */
public interface StorageService {

    void store(String key, ObfuscationTag tag);

    Optional<ObfuscationTag> retrieve(String key);

    boolean contains(String key);

    void remove(String key);

    void clear();

    /**
     * Stores the tag only if the key has no mapping yet.
     *
     * <p>The default implementation serializes a {@link #retrieve}/{@link #store} pair
     * on this instance, which is atomic within one JVM only. Implementations backed by
     * a shared store should override it with an atomic operation (e.g. Redis
     * {@code SET NX}) so concurrent instances cannot overwrite each other's mappings.
     *
     * @return the existing tag if the key was already mapped (nothing is stored), or
     * empty if the tag was stored
     */
    default Optional<ObfuscationTag> storeIfAbsent(String key, ObfuscationTag tag) {
        synchronized (this) {
            Optional<ObfuscationTag> existing = retrieve(key);
            if (existing.isEmpty()) {
                store(key, tag);
            }
            return existing;
        }
    }

    /**
     * Signals that an existing mapping is being used again, so storages with a
     * time-to-live can extend it. The default implementation does nothing.
     */
    default void refresh(String key) {
    }
}
