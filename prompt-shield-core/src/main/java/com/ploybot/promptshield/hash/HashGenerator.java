package com.ploybot.promptshield.hash;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Generates tag identifiers using a keyed HMAC over the input value.
 *
 * <p>A keyed HMAC (instead of a plain hash) prevents anyone who sees a tag from
 * brute-forcing the original value over small input spaces (a DNI has only ~10^8
 * combinations). Without the key, the identifier reveals nothing about the value.
 *
 * <p>If no secret is provided, a random per-instance key is generated: identifiers
 * are then not reproducible across application restarts, which is fine as long as
 * tags are restored by the same instance. Persisted mappings cannot be reused after
 * a restart unless a stable secret is configured.
 */
public class HashGenerator {

    private static final HexFormat HEX = HexFormat.of();

    private final String algorithm;
    private final int hashLength;
    private final SecretKeySpec key;
    /** Initialized prototype; cloned per call because {@link Mac} is not thread-safe. */
    private final Mac prototype;

    public HashGenerator(String algorithm, int hashLength) {
        this(algorithm, hashLength, null);
    }

    public HashGenerator(String algorithm, int hashLength, String secret) {
        if (algorithm == null || algorithm.isBlank()) {
            throw new IllegalArgumentException("algorithm must not be null or blank");
        }
        if (hashLength < 1) {
            throw new IllegalArgumentException("hashLength must be greater than zero");
        }
        this.algorithm = algorithm;
        this.hashLength = hashLength;
        byte[] keyBytes;
        if (secret == null || secret.isBlank()) {
            keyBytes = new byte[32];
            new SecureRandom().nextBytes(keyBytes);
        } else {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.key = new SecretKeySpec(keyBytes, macAlgorithm());
        this.prototype = newMac();
    }

    public HashGenerator() {
        this("SHA-256", 6);
    }

    private String macAlgorithm() {
        return "Hmac" + algorithm.replace("-", "");
    }

    private Mac newMac() {
        try {
            Mac mac = Mac.getInstance(macAlgorithm());
            mac.init(key);
            return mac;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Algorithm not found: " + macAlgorithm(), e);
        } catch (InvalidKeyException e) {
            throw new IllegalArgumentException("Invalid HMAC key", e);
        }
    }

    private Mac mac() {
        try {
            return (Mac) prototype.clone();
        } catch (CloneNotSupportedException e) {
            return newMac();
        }
    }

    public String generate(String value) {
        String full = generateFull(value);
        return full.substring(0, Math.min(hashLength, full.length()));
    }

    /**
     * Returns the full (untruncated) hex-encoded HMAC of the value. Used to extend
     * an identifier deterministically when a truncated prefix collides.
     */
    public String generateFull(String value) {
        return HEX.formatHex(mac().doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public int getHashLength() {
        return hashLength;
    }
}
