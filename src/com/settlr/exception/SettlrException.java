package com.settlr.exception;

/**
 * Base class for every error Settlr itself throws.
 * It is a checked exception, so the compiler makes callers handle it.
 * Screens catch SettlrException and show getMessage() to the user, so every
 * message should be a short sentence a user can read.
 */
public class SettlrException extends Exception {

    public SettlrException(String message) {
        super(message);
    }

    /** Use this when wrapping another exception, so the original cause isn't lost. */
    public SettlrException(String message, Throwable cause) {
        super(message, cause);
    }
}
