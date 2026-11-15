package com.settlr.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Hashes passwords with PBKDF2 (built into Java) and a random salt for every password.
 *
 * Why not plain SHA-256: it is fast, and without a salt two people with the same password get
 * the same hash, so a list of precomputed hashes cracks common passwords instantly.
 * PBKDF2 repeats the hashing many times (slow on purpose) and mixes in a random salt, so the
 * same password gives a different hash for every account.
 *
 * A stored hash looks like  p1$&lt;salt&gt;$&lt;hash&gt;  (63 characters, so it fits the existing
 * CHAR(64) column): "p1" names the method and settings below, then the salt and the PBKDF2
 * result, both in Base64. Accounts made before this change still have a 64-character SHA-256
 * hash; they can still log in, and AuthService swaps it for a PBKDF2 one when they do.
 */
public final class PasswordHasher {

    private static final String VERSION = "p1";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int SALT_BYTES = 12;   // 16 Base64 characters
    private static final int HASH_BITS = 256;   // 43 Base64 characters
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private PasswordHasher() { }

    /** A new salted PBKDF2 hash of the password, e.g. "p1$Wq2...$h7K...". */
    public static String hash(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Password cannot be null.");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return VERSION + "$" + ENCODER.encodeToString(salt) + "$" + ENCODER.encodeToString(pbkdf2(raw, salt));
    }

    /** True if the password matches the stored hash (either kind). A null or unknown hash never matches. */
    public static boolean matches(String raw, String stored) {
        if (raw == null || stored == null) {
            return false;
        }
        byte[] expected;
        byte[] actual;
        if (isLegacy(stored)) {
            expected = stored.toLowerCase().getBytes(StandardCharsets.UTF_8);
            actual = sha256Hex(raw).getBytes(StandardCharsets.UTF_8);
        } else if (isCurrent(stored)) {
            String[] parts = stored.split("\\$");
            expected = DECODER.decode(parts[2]);
            actual = pbkdf2(raw, DECODER.decode(parts[1]));
        } else {
            return false;
        }
        // isEqual compares in constant time, unlike Arrays.equals or String.equals.
        return MessageDigest.isEqual(expected, actual);
    }

    /** True for an old unsalted SHA-256 hash that should be replaced after the next login. */
    public static boolean needsUpgrade(String stored) {
        return stored != null && isLegacy(stored);
    }

    /** True if the text is a hash this class can check (either kind). Used by User to validate. */
    public static boolean isValidHash(String stored) {
        return stored != null && (isLegacy(stored) || isCurrent(stored));
    }

    /** True for an old 64-character hex SHA-256 hash. */
    private static boolean isLegacy(String stored) {
        return stored.matches("[0-9a-fA-F]{64}");
    }

    /** True for a "p1$salt$hash" PBKDF2 hash. */
    private static boolean isCurrent(String stored) {
        return stored.matches("p1\\$[A-Za-z0-9_-]{16}\\$[A-Za-z0-9_-]{43}");
    }

    /** Runs PBKDF2 over the password and salt ITERATIONS times; gives back the 32-byte result. */
    private static byte[] pbkdf2(String raw, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(raw.toCharArray(), salt, ITERATIONS, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM + " is not available.", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** The old way: lowercase hex SHA-256, kept only to check accounts made before PBKDF2. */
    private static String sha256Hex(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b)); // %02x keeps leading zeroes
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
