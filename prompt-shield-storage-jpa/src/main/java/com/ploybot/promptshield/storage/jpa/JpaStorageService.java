package com.ploybot.promptshield.storage.jpa;

import com.ploybot.promptshield.model.ObfuscationTag;
import com.ploybot.promptshield.storage.StorageService;
import com.ploybot.promptshield.storage.jpa.entity.ObfuscationTagEntity;
import com.ploybot.promptshield.storage.jpa.repository.ObfuscationTagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public class JpaStorageService implements StorageService {

    private static final Logger logger = LoggerFactory.getLogger(JpaStorageService.class);

    private final ObfuscationTagRepository repository;
    private final long ttlHours;

    public JpaStorageService(ObfuscationTagRepository repository, long ttlHours) {
        this.repository = repository;
        this.ttlHours = ttlHours;
    }

    @Override
    @Transactional
    public void store(String hash, ObfuscationTag tag) {
        ObfuscationTagEntity entity = new ObfuscationTagEntity(
                hash,
                tag.getType(),
                tag.getOriginalValue()
        );
        repository.save(entity);
        logger.debug("Stored obfuscation tag for hash: {}", hash);
    }

    @Override
    public Optional<ObfuscationTag> retrieve(String hash) {
        return repository.findById(hash)
                .map(entity -> new ObfuscationTag(
                        entity.getType(),
                        entity.getHash(),
                        entity.getOriginalValue()
                ));
    }

    @Override
    public boolean contains(String hash) {
        return repository.existsById(hash);
    }

    @Override
    @Transactional
    public void remove(String hash) {
        repository.deleteById(hash);
        logger.debug("Removed obfuscation tag for hash: {}", hash);
    }

    @Override
    @Transactional
    public void clear() {
        repository.deleteAll();
        logger.debug("Cleared all obfuscation tags");
    }

    @Transactional
    public int cleanupExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(ttlHours);
        int deleted = repository.deleteByCreatedAtBefore(cutoff);
        logger.debug("Cleaned up {} expired obfuscation tags", deleted);
        return deleted;
    }

    public long size() {
        return repository.count();
    }

    public long countByType(String type) {
        return repository.countByType(type);
    }
}
