package com.obfuscador.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashGenerator {

    private final String algorithm;
    private final int hashLength;

    public HashGenerator(String algorithm, int hashLength) {
        this.algorithm = algorithm;
        this.hashLength = hashLength;
    }

    public HashGenerator() {
        this("SHA-256", 6);
    }

    public String generate(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            String hexHash = bytesToHex(hashBytes);
            return hexHash.substring(0, Math.min(hashLength, hexHash.length()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm not found: " + algorithm, e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public int getHashLength() {
        return hashLength;
    }
}
