package com.settlr.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** Checks the salted PBKDF2 hashes, and that accounts with an old SHA-256 hash still work. */
public class PasswordHasherTest {

    @Test
    public void theSamePasswordGetsADifferentHashEveryTime() {
        assertNotEquals(PasswordHasher.hash("settlr123"), PasswordHasher.hash("settlr123")); // random salt
    }

    @Test
    public void theRightPasswordMatchesAndAWrongOneDoesnt() {
        String hash = PasswordHasher.hash("settlr123");
        assertTrue(PasswordHasher.matches("settlr123", hash));
        assertFalse(PasswordHasher.matches("settlr124", hash));
        assertFalse(PasswordHasher.matches("settlr123", null));
    }

    @Test
    public void aHashFitsTheDatabaseColumn() {
        String hash = PasswordHasher.hash("a much longer password with spaces 123");
        assertEquals(63, hash.length()); // password_hash is CHAR(64)
        assertTrue(hash.startsWith("p1$"));
    }

    @Test
    public void anOldSha256HashStillMatchesAndIsMarkedForUpgrade() {
        String old = sha256("settlr123");
        assertTrue(PasswordHasher.matches("settlr123", old));
        assertTrue(PasswordHasher.needsUpgrade(old));
        assertFalse(PasswordHasher.needsUpgrade(PasswordHasher.hash("settlr123")));
    }

    @Test
    public void somethingThatIsntAHashNeverMatches() {
        assertFalse(PasswordHasher.isValidHash("settlr123"));
        assertFalse(PasswordHasher.matches("settlr123", "settlr123"));
        assertFalse(PasswordHasher.isValidHash(sha256("settlr123").substring(1))); // one character short
    }

    /** SHA-256 in hex, the way passwords were stored before PBKDF2. */
    private static String sha256(String raw) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
