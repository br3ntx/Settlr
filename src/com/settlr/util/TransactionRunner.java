package com.settlr.util;

import com.settlr.exception.SettlrException;

/**
 * Runs work as one transaction: "save all of these, or none of them".
 * Services get one from their DaoFactory, so the app uses real MySQL transactions
 * (Transaction.MYSQL) and the tests use an in-memory one, without the services knowing which.
 */
public interface TransactionRunner {

    /** Runs the work as one transaction and gives back its result. */
    <T> T get(String failMessage, Transaction.WorkWithResult<T> work) throws SettlrException;

    /** Runs the work as one transaction, for work that gives nothing back. */
    default void run(String failMessage, Transaction.Work work) throws SettlrException {
        get(failMessage, c -> {
            work.run(c);
            return null;
        });
    }
}
