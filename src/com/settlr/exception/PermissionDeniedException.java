package com.settlr.exception;

/**
 * The current user isn't allowed to do this, e.g. a Member trying an Admin-only action.
 */
public class PermissionDeniedException extends SettlrException {

    public PermissionDeniedException(String message) {
        super(message);
    }

    /** Use this when wrapping another exception, so the original cause isn't lost. */
    public PermissionDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
