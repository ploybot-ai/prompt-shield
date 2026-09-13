package com.ploybot.promptshield.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class ObfuscationTag {

    private final String type;
    private final String hash;
    private final String originalValue;

    @JsonCreator
    public ObfuscationTag(
            @JsonProperty("type") String type,
            @JsonProperty("hash") String hash,
            @JsonProperty("originalValue") String originalValue) {
        this.type = type;
        this.hash = hash;
        this.originalValue = originalValue;
    }

    public String getType() {
        return type;
    }

    public String getHash() {
        return hash;
    }

    public String getOriginalValue() {
        return originalValue;
    }

    /**
     * @deprecated since 1.4.0: this legacy {@code <TYPE_hash>} form is not the tag that
     * appears in obfuscated text, which depends on the configured delimiters. Use
     * {@code ObfuscationEngine#formatTag(ObfuscationTag)} instead.
     */
    @Deprecated(since = "1.4.0")
    @JsonIgnore
    public String getTag() {
        return "<" + type + "_" + hash + ">";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObfuscationTag that = (ObfuscationTag) o;
        return Objects.equals(type, that.type) && Objects.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, hash);
    }

    @Override
    public String toString() {
        return "ObfuscationTag{" +
                "type='" + type + '\'' +
                ", hash='" + hash + '\'' +
                '}';
    }
}
