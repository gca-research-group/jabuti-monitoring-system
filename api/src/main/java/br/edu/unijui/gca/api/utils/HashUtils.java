package br.edu.unijui.gca.api.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class HashUtils {

    private HashUtils() {
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash API key", e);
        }
    }
}
