package com.settlr.exception;

/**
 * The user typed something invalid, e.g. an empty title or points outside 1-5.
 */
public class ValidationException extends SettlrException {

    public ValidationException(String message) {
        super(message);
    }

    /** Use this when wrapping another exception, so the original cause isn't lost. */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
