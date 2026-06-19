package com.ploybot.promptshield.storage;

import com.ploybot.promptshield.model.ObfuscationTag;
import java.util.Optional;

public interface StorageService {

    void store(String hash, ObfuscationTag tag);

    Optional<ObfuscationTag> retrieve(String hash);

    boolean contains(String hash);

    void remove(String hash);

    void clear();
}
