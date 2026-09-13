package com.ploybot.promptshield.storage.jpa.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "obfuscation_tags")
public class ObfuscationTagEntity {

    /** SHA-256 (hex) of the scoped key, so arbitrarily long scope ids always fit. */
    @Id
    @Column(name = "hash", length = 64)
    private String hash;

    @Column(name = "type", length = 50, nullable = false)
    private String type;

    @Column(name = "original_value", length = 4096, nullable = false)
    private String originalValue;

    /** Creation time, moved forward whenever the mapping is used again. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public ObfuscationTagEntity() {
    }

    public ObfuscationTagEntity(String hash, String type, String originalValue) {
        this.hash = hash;
        this.type = type;
        this.originalValue = originalValue;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOriginalValue() {
        return originalValue;
    }

    public void setOriginalValue(String originalValue) {
        this.originalValue = originalValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
