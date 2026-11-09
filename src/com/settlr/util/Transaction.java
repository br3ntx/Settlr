package com.settlr.util;

import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * "Save all of these, or none of them." Runs several DAO calls on one connection and commits
 * them together; if anything fails, everything is undone (rolled back).
 *
 *     Transaction.run("Could not save the task.", c -> {
 *         taskDao.insert(c, task);
 *         assignmentDao.insert(c, firstTurn);
 *     });
 *
 *     int left = Transaction.get("Could not save your purchase.", c -> { ...; return balance; });
 *
 * The services decide where a transaction starts and ends; the DAOs still hold every SQL
 * statement and take the Connection as their first parameter.
 */
public final class Transaction {

    /** The work to do inside the transaction, when it gives nothing back. */
    @FunctionalInterface
    public interface Work {
        void run(Connection c) throws SettlrException;
    }

    /** The work to do inside the transaction, when it gives back a result. */
    @FunctionalInterface
    public interface WorkWithResult<T> {
        T run(Connection c) throws SettlrException;
    }

    private Transaction() { }

    /** Runs the work as one transaction. failMessage is what the user sees if the database fails. */
    public static void run(String failMessage, Work work) throws SettlrException {
        get(failMessage, c -> {
            work.run(c);
            return null;
        });
    }

    /** Same as run(...), for work that gives back a result. */
    public static <T> T get(String failMessage, WorkWithResult<T> work) throws SettlrException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SettlrException | RuntimeException e) {
                rollback(c, e);
                throw e; // the real error, e.g. "You need 20 more XP."
            } catch (SQLException e) {
                rollback(c, e);
                throw new DataAccessException(failMessage, e);
            }
        } catch (SQLException e) {
            throw new DataAccessException(failMessage, e); // couldn't connect or commit
        }
    }

    /**
     * Undoes the transaction. If even the undo fails, its error is attached to the original one
     * (as a "suppressed" exception) instead of replacing it, so the real cause is never lost.
     */
    private static void rollback(Connection c, Exception original) {
        try {
            c.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }
}
