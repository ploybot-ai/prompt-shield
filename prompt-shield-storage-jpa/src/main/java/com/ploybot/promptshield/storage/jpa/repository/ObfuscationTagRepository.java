package com.ploybot.promptshield.storage.jpa.repository;

import com.ploybot.promptshield.storage.jpa.entity.ObfuscationTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * Not picked up by component or repository scanning: the storage auto-configuration
 * creates it directly so it never interferes with the application's own repositories.
 */
public interface ObfuscationTagRepository extends JpaRepository<ObfuscationTagEntity, String> {

    @Modifying
    @Query("DELETE FROM ObfuscationTagEntity e WHERE e.createdAt < :cutoffDate")
    int deleteByCreatedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    /** Moves the timestamp forward, only when it is older than {@code olderThan}. */
    @Modifying
    @Query("UPDATE ObfuscationTagEntity e SET e.createdAt = :now WHERE e.hash = :hash AND e.createdAt < :olderThan")
    int touch(@Param("hash") String hash, @Param("now") LocalDateTime now, @Param("olderThan") LocalDateTime olderThan);

    long countByType(String type);
}
