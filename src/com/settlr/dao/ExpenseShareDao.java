package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.ExpenseShare;
import com.settlr.util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the expense_shares table. A share is one person's portion of an expense.
 * settled_at = NULL means unpaid; a date means settled. This class only stores and finds
 * shares; deciding WHEN a share is settled is a business rule for the services.
 */
public class ExpenseShareDao implements Dao<ExpenseShare> {

    private static final String COLUMNS = "share_id, expense_id, user_id, amount_owed, settled_at";

    /** Returns the share, or null if it does not exist. */
    @Override
    public ExpenseShare findById(int id) throws DataAccessException {
        String sql = "SELECT " + COLUMNS + " FROM expense_shares WHERE share_id = ?";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the share.", ex);
        }
    }

    /** Saves a share on its own connection and returns the new id. */
    @Override
    public int insert(ExpenseShare share) throws DataAccessException {
        try (Connection con = DBConnection.getInstance().getConnection()) {
            return insert(con, share);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save the share.", ex);
        }
    }

    /** Saves a share using a connection the caller controls (for transactions). */
    public int insert(Connection con, ExpenseShare share) throws DataAccessException {
        String sql = "INSERT INTO expense_shares (expense_id, user_id, amount_owed, settled_at) "
                + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, share.getExpenseId());
            ps.setInt(2, share.getUserId());
            ps.setBigDecimal(3, share.getAmountOwed());
            setNullableDateTime(ps, 4, share.getSettledAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new SQLException("No generated id was returned.");
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save the share.", ex);
        }
    }

    /** Updates the amount and the settled time on its own connection. */
    @Override
    public void update(ExpenseShare share) throws DataAccessException {
        try (Connection con = DBConnection.getInstance().getConnection()) {
            update(con, share);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not update the share.", ex);
        }
    }

    /** Same update, but inside the caller's transaction. */
    public void update(Connection con, ExpenseShare share) throws DataAccessException {
        String sql = "UPDATE expense_shares SET amount_owed = ?, settled_at = ? WHERE share_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setBigDecimal(1, share.getAmountOwed());
            setNullableDateTime(ps, 2, share.getSettledAt());
            ps.setInt(3, share.getId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not update the share.", ex);
        }
    }

    @Override
    public void delete(int id) throws DataAccessException {
        String sql = "DELETE FROM expense_shares WHERE share_id = ?";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not delete the share.", ex);
        }
    }

    /** Unpaid shares that debtorId owes for expenses that creditorId paid. Oldest due date first. */
    public List<ExpenseShare> findUnsettled(int debtorId, int creditorId) throws DataAccessException {
        String sql = "SELECT s.share_id, s.expense_id, s.user_id, s.amount_owed, s.settled_at "
                + "FROM expense_shares s JOIN expenses e ON e.expense_id = s.expense_id "
                + "WHERE s.user_id = ? AND e.paid_by = ? AND s.settled_at IS NULL "
                + "ORDER BY e.due_date, s.share_id";
        return query(sql, debtorId, creditorId);
    }

    /** Unpaid shares of this user whose expense due date is before today. */
    public List<ExpenseShare> findUnsettledPastDue(int userId, LocalDate today) throws DataAccessException {
        String sql = "SELECT s.share_id, s.expense_id, s.user_id, s.amount_owed, s.settled_at "
                + "FROM expense_shares s JOIN expenses e ON e.expense_id = s.expense_id "
                + "WHERE s.user_id = ? AND s.settled_at IS NULL AND e.due_date < ? "
                + "ORDER BY e.due_date, s.share_id";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setObject(2, today);
            return readAll(ps);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the unpaid shares.", ex);
        }
    }

    /** Every share (paid or not) of every expense in this household, for "your share" on the Expenses page. */
    public List<ExpenseShare> findByHousehold(int householdId) throws DataAccessException {
        String sql = "SELECT s.share_id, s.expense_id, s.user_id, s.amount_owed, s.settled_at "
                + "FROM expense_shares s JOIN expenses e ON e.expense_id = s.expense_id "
                + "WHERE e.household_id = ? "
                + "ORDER BY s.expense_id, s.share_id";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            return readAll(ps);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the expense shares.", ex);
        }
    }

    // ---------- private helpers ----------

    private List<ExpenseShare> query(String sql, int first, int second) throws DataAccessException {
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, first);
            ps.setInt(2, second);
            return readAll(ps);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the unpaid shares.", ex);
        }
    }

    private List<ExpenseShare> readAll(PreparedStatement ps) throws SQLException {
        List<ExpenseShare> result = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(map(rs));
            }
        }
        return result;
    }

    private ExpenseShare map(ResultSet rs) throws SQLException {
        BigDecimal owed = rs.getBigDecimal("amount_owed");
        return new ExpenseShare(
                rs.getInt("share_id"),
                rs.getInt("expense_id"),
                rs.getInt("user_id"),
                owed,
                rs.getObject("settled_at", LocalDateTime.class)); // null = unpaid
    }

    private void setNullableDateTime(PreparedStatement ps, int index, LocalDateTime value)
            throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TIMESTAMP);
        } else {
            ps.setObject(index, value);
        }
    }
}