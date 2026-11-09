package com.settlr.dao.mysql;

import com.settlr.exception.DataAccessException;
import com.settlr.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * The shared JDBC steps every MySQL DAO used to copy by hand (Template Method pattern).
 *
 * Running a query is always the same recipe:
 *   1. open a connection (or use the caller's, inside a transaction)
 *   2. prepare the SQL and fill in the ? values
 *   3. run it and turn each row into an object
 *   4. close everything, and if anything failed, throw a DataAccessException with a readable
 *      message AND the original SQL error attached (so the real cause is never lost)
 *
 * The recipe is written once, here. The one step that differs per table is step 3, turning a
 * row into an object, so each DAO only fills in mapRow(...):
 *
 *     public class MySqlTaskDao extends AbstractDao<Task> implements TaskDao {
 *         protected Task mapRow(ResultSet rs) throws SQLException { ... }
 *
 *         public List<Task> findAllActive() throws DataAccessException {
 *             return queryList(SELECT + "WHERE is_archived = FALSE", "Could not load the tasks.");
 *         }
 *     }
 *
 * Every method has a version that takes a Connection, for use inside Transaction.run(...).
 *
 * @param <T> what one row of this DAO's table becomes, e.g. Task
 */
public abstract class AbstractDao<T> {

    /** Turns one row into an object. The step each DAO fills in. */
    protected abstract T mapRow(ResultSet rs) throws SQLException;

    /** Turns one row into something other than T (for joins and single columns). */
    @FunctionalInterface
    protected interface RowMapper<R> {
        R map(ResultSet rs) throws SQLException;
    }

    /** JDBC work that doesn't fit the helpers below. */
    @FunctionalInterface
    protected interface SqlWork<R> {
        R run(Connection c) throws SQLException, DataAccessException;
    }

    // =====================================================================
    //  Reading
    // =====================================================================

    /** Every row the query returns, as objects. */
    protected List<T> queryList(String sql, String failMessage, Object... params) throws DataAccessException {
        return query(sql, this::mapRow, failMessage, params);
    }

    protected List<T> queryList(Connection c, String sql, String failMessage, Object... params)
            throws DataAccessException {
        return query(c, sql, this::mapRow, failMessage, params);
    }

    /** The first row the query returns, or null if there are none. */
    protected T queryOne(String sql, String failMessage, Object... params) throws DataAccessException {
        List<T> rows = queryList(sql, failMessage, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    protected T queryOne(Connection c, String sql, String failMessage, Object... params)
            throws DataAccessException {
        List<T> rows = queryList(c, sql, failMessage, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Every row, turned into objects by the given mapper instead of mapRow. */
    protected <R> List<R> query(String sql, RowMapper<R> mapper, String failMessage, Object... params)
            throws DataAccessException {
        return withConnection(failMessage, c -> runQuery(c, sql, mapper, params));
    }

    protected <R> List<R> query(Connection c, String sql, RowMapper<R> mapper, String failMessage,
                                Object... params) throws DataAccessException {
        try {
            return runQuery(c, sql, mapper, params);
        } catch (SQLException e) {
            throw new DataAccessException(failMessage, e);
        }
    }

    /** True if the query returns at least one row. */
    protected boolean exists(String sql, String failMessage, Object... params) throws DataAccessException {
        return !query(sql, rs -> Boolean.TRUE, failMessage, params).isEmpty();
    }

    // =====================================================================
    //  Writing
    // =====================================================================

    /** Runs an INSERT, UPDATE or DELETE and returns how many rows it changed. */
    protected int update(String sql, String failMessage, Object... params) throws DataAccessException {
        return withConnection(failMessage, c -> runUpdate(c, sql, params));
    }

    protected int update(Connection c, String sql, String failMessage, Object... params)
            throws DataAccessException {
        try {
            return runUpdate(c, sql, params);
        } catch (SQLException e) {
            throw new DataAccessException(failMessage, e);
        }
    }

    /** Runs an INSERT and returns the id the database gave the new row. */
    protected int insert(String sql, String failMessage, Object... params) throws DataAccessException {
        return withConnection(failMessage, c -> runInsert(c, sql, failMessage, params));
    }

    protected int insert(Connection c, String sql, String failMessage, Object... params)
            throws DataAccessException {
        try {
            return runInsert(c, sql, failMessage, params);
        } catch (SQLException e) {
            throw new DataAccessException(failMessage, e);
        }
    }

    /** Opens a connection, runs the work, closes it, and wraps any SQL error with its cause. */
    protected <R> R withConnection(String failMessage, SqlWork<R> work) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            return work.run(c);
        } catch (SQLException e) {
            throw new DataAccessException(failMessage, e);
        }
    }

    // =====================================================================
    //  The fixed steps
    // =====================================================================

    private static <R> List<R> runQuery(Connection c, String sql, RowMapper<R> mapper, Object... params)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            List<R> rows = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(mapper.map(rs));
                }
            }
            return rows;
        }
    }

    private static int runUpdate(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    private static int runInsert(Connection c, String sql, String failMessage, Object... params)
            throws SQLException, DataAccessException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new DataAccessException(failMessage + " (the database gave no id back)");
        }
    }

    /**
     * Fills in the ? values in order. Enums are saved by name (they match the SQL ENUMs), null
     * becomes SQL NULL, and dates, numbers and text go in as they are.
     */
    protected static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object value = params[i];
            if (value == null) {
                ps.setNull(i + 1, Types.NULL);
            } else if (value instanceof Enum) {
                ps.setString(i + 1, ((Enum<?>) value).name());
            } else {
                ps.setObject(i + 1, value);
            }
        }
    }
}
