package org.lab5.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PasswordHasher {
    public static String sha224(String password) {
        if (password == null) {
            throw new IllegalArgumentException("password must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-224");
            byte[] bytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-224 is not available", e);
        }
    }
}
