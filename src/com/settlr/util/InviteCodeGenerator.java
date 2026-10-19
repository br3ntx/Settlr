package com.settlr.util;

import java.security.SecureRandom;

/**
 * Generates 6-character household invite codes. Uniqueness across households
 * must be enforced by the database/service, not by this class.
 */
public final class InviteCodeGenerator {
    /** No 0/O or 1/I, to avoid lookalike characters. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private InviteCodeGenerator() { }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            // nextInt(bound) is unbiased, unlike a plain modulo.
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}