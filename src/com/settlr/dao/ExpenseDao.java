package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseCategory;
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
 * All SQL for the expenses table. Services never see SQL; they call these methods.
 * The insert has a Connection version so ExpenseService can save an expense and its
 * shares in ONE transaction (all saved, or none).
 */
public class ExpenseDao implements Dao<Expense> {

    private static final String COLUMNS =
            "expense_id, household_id, bill_id, title, category, amount, paid_by, due_date, created_by, created_at";

    /** Returns the expense, or null if there is no expense with this id. */
    @Override
    public Expense findById(int id) throws DataAccessException {
        String sql = "SELECT " + COLUMNS + " FROM expenses WHERE expense_id = ?";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the expense.", ex);
        }
    }

    /** Saves a new expense on its own connection and returns the new id. */
    @Override
    public int insert(Expense expense) throws DataAccessException {
        try (Connection con = DBConnection.getInstance().getConnection()) {
            return insert(con, expense);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save the expense.", ex);
        }
    }

    /**
     * Saves a new expense using a connection the caller controls (for transactions).
     * created_at is left out so the database fills it with the current time.
     */
    public int insert(Connection con, Expense e) throws DataAccessException {
        String sql = "INSERT INTO expenses (household_id, bill_id, title, category, amount, "
                + "paid_by, due_date, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, e.getHouseholdId());
            if (e.getBillId() == null) {
                ps.setNull(2, Types.INTEGER); // bill_id is NULL for hand-made expenses
            } else {
                ps.setInt(2, e.getBillId());
            }
            ps.setString(3, e.getTitle());
            ps.setString(4, e.getCategory().name());
            ps.setBigDecimal(5, e.getAmount());
            ps.setInt(6, e.getPaidBy());
            ps.setObject(7, e.getDueDate());
            ps.setInt(8, e.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new SQLException("No generated id was returned.");
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save the expense.", ex);
        }
    }

    /** Updates the editable fields. Household, bill and creator never change. */
    @Override
    public void update(Expense e) throws DataAccessException {
        String sql = "UPDATE expenses SET title = ?, category = ?, amount = ?, paid_by = ?, "
                + "due_date = ? WHERE expense_id = ?";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, e.getTitle());
            ps.setString(2, e.getCategory().name());
            ps.setBigDecimal(3, e.getAmount());
            ps.setInt(4, e.getPaidBy());
            ps.setObject(5, e.getDueDate());
            ps.setInt(6, e.getId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not update the expense.", ex);
        }
    }

    /** Deletes the expense. Its shares and their payments go too (ON DELETE CASCADE). */
    @Override
    public void delete(int id) throws DataAccessException {
        String sql = "DELETE FROM expenses WHERE expense_id = ?";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not delete the expense.", ex);
        }
    }

    /** Newest first. Pass null as filterOrNull to get every category. */
    public List<Expense> findByHousehold(int householdId, ExpenseCategory filterOrNull)
            throws DataAccessException {
        // Only fixed text is appended here; the values still go through "?" placeholders.
        String sql = "SELECT " + COLUMNS + " FROM expenses WHERE household_id = ?"
                + (filterOrNull == null ? "" : " AND category = ?")
                + " ORDER BY created_at DESC, expense_id DESC";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            if (filterOrNull != null) {
                ps.setString(2, filterOrNull.name());
            }
            List<Expense> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
            return result;
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the expenses.", ex);
        }
    }

    /** True if this recurring bill already has an expense with this due date. */
    public boolean existsForBill(int billId, LocalDate dueDate) throws DataAccessException {
        String sql = "SELECT 1 FROM expenses WHERE bill_id = ? AND due_date = ? LIMIT 1";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, billId);
            ps.setObject(2, dueDate);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not check the recurring bill.", ex);
        }
    }

    /** Turns the current row into an Expense. */
    private Expense map(ResultSet rs) throws SQLException {
        return new Expense(
                rs.getInt("expense_id"),
                rs.getInt("household_id"),
                rs.getObject("bill_id", Integer.class), // null stays null
                rs.getString("title"),
                ExpenseCategory.valueOf(rs.getString("category")),
                rs.getBigDecimal("amount"),
                rs.getInt("paid_by"),
                rs.getObject("due_date", LocalDate.class),
                rs.getInt("created_by"),
                rs.getObject("created_at", LocalDateTime.class));
    }
}