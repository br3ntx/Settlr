package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.Payment;
import com.settlr.model.PaymentStatus;
import com.settlr.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the payments table. It stores whatever status the Payment object has.
 * Saving a payment does NOT confirm it: a new payment is PENDING, and only
 * PaymentService (later) decides to confirm or reject one.
 */
public class PaymentDao implements Dao<Payment> {

    private static final String COLUMNS =
            "payment_id, share_id, from_user, to_user, amount, paid_at, status, confirmed_at, note";

    /** Returns the payment, or null if it does not exist. */
    @Override
    public Payment findById(int id) throws DataAccessException {
        String sql = "SELECT " + COLUMNS + " FROM payments WHERE payment_id = ?";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the payment.", ex);
        }
    }

    /** Saves a new payment on its own connection and returns its id. */
    @Override
    public int insert(Payment p) throws DataAccessException {
        try (Connection con = DBConnection.getInstance().getConnection()) {
            return insert(con, p);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save the payment.", ex);
        }
    }

    /** Saves a new payment using a connection the caller controls (for transactions). */
    public int insert(Connection con, Payment p) throws DataAccessException {
        String sql = "INSERT INTO payments (share_id, from_user, to_user, amount, paid_at, "
                + "status, confirmed_at, note) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, p.getShareId());
            ps.setInt(2, p.getFromUser());
            ps.setInt(3, p.getToUser());
            ps.setBigDecimal(4, p.getAmount());
            ps.setObject(5, p.getPaidAt());
            ps.setString(6, p.getStatus().name());
            if (p.getConfirmedAt() == null) {
                ps.setNull(7, Types.TIMESTAMP);
            } else {
                ps.setObject(7, p.getConfirmedAt());
            }
            ps.setString(8, p.getNote()); // setString accepts null for an empty note
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new SQLException("No generated id was returned.");
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save the payment.", ex);
        }
    }

    /** Saves the status, confirmed time and note on its own connection. */
    @Override
    public void update(Payment p) throws DataAccessException {
        try (Connection con = DBConnection.getInstance().getConnection()) {
            update(con, p);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not update the payment.", ex);
        }
    }

    /** Same update, but inside the caller's transaction. */
    public void update(Connection con, Payment p) throws DataAccessException {
        String sql = "UPDATE payments SET status = ?, confirmed_at = ?, note = ? WHERE payment_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, p.getStatus().name());
            if (p.getConfirmedAt() == null) {
                ps.setNull(2, Types.TIMESTAMP);
            } else {
                ps.setObject(2, p.getConfirmedAt());
            }
            ps.setString(3, p.getNote());
            ps.setInt(4, p.getId());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not update the payment.", ex);
        }
    }

    @Override
    public void delete(int id) throws DataAccessException {
        String sql = "DELETE FROM payments WHERE payment_id = ?";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not delete the payment.", ex);
        }
    }

    /** PENDING payments that receiverId still has to confirm or reject, oldest first. */
    public List<Payment> findWaitingFor(int receiverId) throws DataAccessException {
        String sql = "SELECT " + COLUMNS + " FROM payments WHERE to_user = ? AND status = ? "
                + "ORDER BY paid_at, payment_id";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, receiverId);
            ps.setString(2, PaymentStatus.PENDING.name());
            List<Payment> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
            return result;
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the payments.", ex);
        }
    }

    /** The PENDING payment of this share, or null if it has none (a share can have only one). */
    public Payment findPendingForShare(int shareId) throws DataAccessException {
        String sql = "SELECT " + COLUMNS + " FROM payments WHERE share_id = ? AND status = ? LIMIT 1";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, shareId);
            ps.setString(2, PaymentStatus.PENDING.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load the payment.", ex);
        }
    }

    /**
     * True if any share of this expense has a CONFIRMED payment. ExpenseService uses it for
     * the rule "delete only while no share has a confirmed payment".
     */
    public boolean existsConfirmedForExpense(int expenseId) throws DataAccessException {
        String sql = "SELECT 1 FROM payments p JOIN expense_shares s ON s.share_id = p.share_id "
                + "WHERE s.expense_id = ? AND p.status = ? LIMIT 1";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, expenseId);
            ps.setString(2, PaymentStatus.CONFIRMED.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not check the payments.", ex);
        }
    }

    private Payment map(ResultSet rs) throws SQLException {
        return new Payment(
                rs.getInt("payment_id"),
                rs.getInt("share_id"),
                rs.getInt("from_user"),
                rs.getInt("to_user"),
                rs.getBigDecimal("amount"),
                rs.getObject("paid_at", LocalDateTime.class),
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getObject("confirmed_at", LocalDateTime.class), // null until confirmed
                rs.getString("note"));                              // null if no note
    }
}