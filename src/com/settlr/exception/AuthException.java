package com.settlr.exception;

/**
 * Login or registration failed, e.g. wrong username or password.
 */
public class AuthException extends SettlrException {

    public AuthException(String message) {
        super(message);
    }

    /** Use this when wrapping another exception, so the original cause isn't lost. */
    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
