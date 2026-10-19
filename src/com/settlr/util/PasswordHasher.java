package com.settlr.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hashes passwords with plain SHA-256 as the master document specifies (64 hex characters).
 * Note: unsalted SHA-256 is NOT recommended for production password storage;
 * switching to BCrypt/Argon2/PBKDF2 would be a separate project decision.
 */
public final class PasswordHasher {
    private PasswordHasher() { }

    /** Returns the lowercase 64-character hex SHA-256 of the password (UTF-8). */
    public static String hash(String raw) {
        if (raw == null) throw new IllegalArgumentException("Password cannot be null.");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b)); // %02x keeps leading zeroes
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    /** True if hashing raw gives the supplied hash. A null hash never matches. */
    public static boolean matches(String raw, String hash) {
        String computed = hash(raw);
        if (hash == null) return false;
        // isEqual compares in constant time, unlike String.equals.
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                hash.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}