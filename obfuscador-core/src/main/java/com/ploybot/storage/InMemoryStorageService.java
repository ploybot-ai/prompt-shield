package com.ploybot.storage;

import com.ploybot.model.ObfuscationTag;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class InMemoryStorageService implements StorageService {

    private final Map<String, ObfuscationTag> storage = new ConcurrentHashMap<>();

    @Override
    public void store(String hash, ObfuscationTag tag) {
        storage.put(hash, tag);
    }

    @Override
    public Optional<ObfuscationTag> retrieve(String hash) {
        return Optional.ofNullable(storage.get(hash));
    }

    @Override
    public boolean contains(String hash) {
        return storage.containsKey(hash);
    }

    @Override
    public void remove(String hash) {
        storage.remove(hash);
    }

    @Override
    public void clear() {
        storage.clear();
    }

    public int size() {
        return storage.size();
    }
}
