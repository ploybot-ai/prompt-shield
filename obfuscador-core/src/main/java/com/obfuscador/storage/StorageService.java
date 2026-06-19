package com.obfuscador.storage;

import com.obfuscador.model.ObfuscationTag;
import java.util.Optional;

public interface StorageService {

    void store(String hash, ObfuscationTag tag);

    Optional<ObfuscationTag> retrieve(String hash);

    boolean contains(String hash);

    void remove(String hash);

    void clear();
}
