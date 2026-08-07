package br.edu.unijui.gca.api.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtils {

    private static final ThreadLocal<MessageDigest> SHA256 =
            ThreadLocal.withInitial(() -> {
                try {
                    return MessageDigest.getInstance("SHA-256");
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            });

    private HashUtils() {
    }

    public static byte[] sha256(byte[] value) {
        MessageDigest digest = SHA256.get();
        digest.reset();
        return digest.digest(value);
    }

    public static byte[] sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }
}
