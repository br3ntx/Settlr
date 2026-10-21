package com.settlr.exception;

/**
 * Something went wrong talking to the database (wraps an SQLException).
 * Thrown only by DAO classes. Its message should never contain raw SQL.
 */
public class DataAccessException extends SettlrException {

    public DataAccessException(String message) {
        super(message);
    }

    /** Use this when wrapping another exception, so the original cause isn't lost. */
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
