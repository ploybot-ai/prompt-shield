package com.ploybot.promptshield.storage.jpa.repository;

import com.ploybot.promptshield.storage.jpa.entity.ObfuscationTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ObfuscationTagRepository extends JpaRepository<ObfuscationTagEntity, String> {

    @Modifying
    @Query("DELETE FROM ObfuscationTagEntity e WHERE e.createdAt < :cutoffDate")
    int deleteByCreatedAtBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    long countByType(String type);
}
