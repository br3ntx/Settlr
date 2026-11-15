package com.settlr.service;

import com.settlr.exception.AuthException;
import com.settlr.model.User;
import com.settlr.util.PasswordHasher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Checks logging in, including swapping an old SHA-256 hash for a PBKDF2 one. */
public class AuthServiceTest {

    private TestHousehold h;

    /** Before each test: a fresh test household in memory (nobody logged in yet). */
    @Before
    public void setUp() throws Exception {
        h = new TestHousehold();
    }

    /** After each test: logs out. */
    @After
    public void tearDown() {
        h.logOut();
    }

    @Test
    public void theRightPasswordLogsYouIntoYourHousehold() throws Exception {
        User me = h.auth().login("ben", TestHousehold.PASSWORD);

        assertEquals(h.ben.getId(), me.getId());
        assertEquals(h.household.getId(), com.settlr.util.Session.getInstance().requireHouseholdId());
    }

    @Test(expected = AuthException.class)
    public void aWrongPasswordIsRefused() throws Exception {
        h.auth().login("ben", "wrong-password");
    }

    @Test
    public void loggingInReplacesAnOldSha256Hash() throws Exception {
        User ben = h.db.users().findById(h.ben.getId());
        ben.setPasswordHash(sha256(TestHousehold.PASSWORD)); // an account from before PBKDF2
        h.db.users().update(ben);
        assertTrue(PasswordHasher.needsUpgrade(h.db.users().findById(h.ben.getId()).getPasswordHash()));

        h.auth().login("ben", TestHousehold.PASSWORD);

        String stored = h.db.users().findById(h.ben.getId()).getPasswordHash();
        assertFalse(PasswordHasher.needsUpgrade(stored));
        assertTrue(PasswordHasher.matches(TestHousehold.PASSWORD, stored)); // same password still works
    }

    /** SHA-256 in hex, the way passwords were stored before PBKDF2. */
    private static String sha256(String raw) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
