package com.settlr.exception;

/**
 * A record that should exist wasn't found, e.g. a task id that was deleted.
 */
public class NotFoundException extends SettlrException {

    public NotFoundException(String message) {
        super(message);
    }

    /** Use this when wrapping another exception, so the original cause isn't lost. */
    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
