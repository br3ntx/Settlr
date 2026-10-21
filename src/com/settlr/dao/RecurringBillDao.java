package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.ExpenseCategory;
import com.settlr.model.RecurringBill;
import com.settlr.util.DBConnection;

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
 * All SQL for recurring_bills and recurring_bill_members. One RecurringBill object
 * covers both tables, so saving a bill also saves who it is split among.
 */
public class RecurringBillDao implements Dao<RecurringBill> {

    private static final String COLUMNS =
            "bill_id, household_id, title, category, amount, paid_by, due_day, "
                    + "is_active, last_generated, created_by, created_at";

    /** Returns the bill with its member ids, or null if it does not exist. */
    @Override
    public RecurringBill findById(int id) throws DataAccessException {
        String sql = "SELECT " + COLUMNS + " FROM recurring_bills WHERE bill_id = ?";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(con, rs) : null;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the recurring bill.", ex);
        }
    }

    /**
     * Saves the bill and its members in one transaction, so we never end up
     * with a bill that has no members. Returns the new bill id.
     */
    @Override
    public int insert(RecurringBill bill) throws DataAccessException {
        String sql = "INSERT INTO recurring_bills (household_id, title, category, amount, paid_by, "
                + "due_day, is_active, last_generated, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getInstance().getConnection()) {
            try {
                con.setAutoCommit(false);
                int newId;
                try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, bill.getHouseholdId());
                    ps.setString(2, bill.getTitle());
                    ps.setString(3, bill.getCategory().name());
                    ps.setBigDecimal(4, bill.getAmount());
                    ps.setInt(5, bill.getPaidBy());
                    ps.setInt(6, bill.getDueDay());
                    ps.setBoolean(7, bill.isActive());
                    setNullableDate(ps, 8, bill.getLastGenerated());
                    ps.setInt(9, bill.getCreatedBy());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("No generated id was returned.");
                        }
                        newId = keys.getInt(1);
                    }
                }
                insertMembers(con, newId, bill.getMemberIds());
                con.commit();
                return newId;
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save the recurring bill.", ex);
        }
    }

    /** Updates the bill and replaces its member list, in one transaction. */
    @Override
    public void update(RecurringBill bill) throws DataAccessException {
        String sql = "UPDATE recurring_bills SET title = ?, category = ?, amount = ?, paid_by = ?, "
                + "due_day = ?, is_active = ?, last_generated = ? WHERE bill_id = ?";
        try (Connection con = DBConnection.getInstance().getConnection()) {
            try {
                con.setAutoCommit(false);
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, bill.getTitle());
                    ps.setString(2, bill.getCategory().name());
                    ps.setBigDecimal(3, bill.getAmount());
                    ps.setInt(4, bill.getPaidBy());
                    ps.setInt(5, bill.getDueDay());
                    ps.setBoolean(6, bill.isActive());
                    setNullableDate(ps, 7, bill.getLastGenerated());
                    ps.setInt(8, bill.getId());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM recurring_bill_members WHERE bill_id = ?")) {
                    ps.setInt(1, bill.getId());
                    ps.executeUpdate();
                }
                insertMembers(con, bill.getId(), bill.getMemberIds());
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                rollback(con, ex);
                throw ex;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not update the recurring bill.", ex);
        }
    }

    /**
     * Deletes the bill (its member rows cascade). This fails if expenses were already
     * created from the bill, because expenses.bill_id has no cascade in the schema.
     */
    @Override
    public void delete(int id) throws DataAccessException {
        String sql = "DELETE FROM recurring_bills WHERE bill_id = ?";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not delete the recurring bill.", ex);
        }
    }

    /** Every active bill of every household; the startup catch-up works on all of them. */
    public List<RecurringBill> findActive() throws DataAccessException {
        String sql = "SELECT " + COLUMNS + " FROM recurring_bills WHERE is_active = TRUE ORDER BY bill_id";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<RecurringBill> bills = new ArrayList<>();
            while (rs.next()) {
                bills.add(map(con, rs));
            }
            return bills;
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the recurring bills.", ex);
        }
    }

    /**
     * Records the due date of the last expense made from this bill. Takes a Connection
     * so it can be part of the same transaction that saves that expense.
     */
    public void updateLastGenerated(Connection con, int billId, LocalDate dueDate)
            throws DataAccessException {
        String sql = "UPDATE recurring_bills SET last_generated = ? WHERE bill_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, dueDate);
            ps.setInt(2, billId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not update the recurring bill.", ex);
        }
    }

    // ---------- private helpers ----------

    private void insertMembers(Connection con, int billId, List<Integer> memberIds) throws SQLException {
        String sql = "INSERT INTO recurring_bill_members (bill_id, user_id) VALUES (?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (int userId : memberIds) {
                ps.setInt(1, billId);
                ps.setInt(2, userId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private List<Integer> loadMemberIds(Connection con, int billId) throws SQLException {
        String sql = "SELECT user_id FROM recurring_bill_members WHERE bill_id = ? ORDER BY user_id";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, billId);
            List<Integer> ids = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt("user_id"));
                }
            }
            return ids;
        }
    }

    private RecurringBill map(Connection con, ResultSet rs) throws SQLException {
        int billId = rs.getInt("bill_id");
        return new RecurringBill(
                billId,
                rs.getInt("household_id"),
                rs.getString("title"),
                ExpenseCategory.valueOf(rs.getString("category")),
                rs.getBigDecimal("amount"),
                rs.getInt("paid_by"),
                rs.getInt("due_day"),
                rs.getBoolean("is_active"),
                rs.getObject("last_generated", LocalDate.class), // null if never generated
                rs.getInt("created_by"),
                rs.getObject("created_at", LocalDateTime.class),
                loadMemberIds(con, billId));
    }

    private void setNullableDate(PreparedStatement ps, int index, LocalDate value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setObject(index, value);
        }
    }

    /** Undoes the transaction; if even the undo fails, keep that error attached to the first one. */
    private void rollback(Connection con, Exception original) {
        try {
            con.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
        }
    }
}