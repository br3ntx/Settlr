package com.settlr.dao.mysql;

import com.settlr.dao.PaymentDao;
import com.settlr.exception.DataAccessException;
import com.settlr.model.Payment;
import com.settlr.model.PaymentStatus;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The payments table in MySQL. A new payment is PENDING until PaymentService confirms it. */
public class MySqlPaymentDao extends AbstractDao<Payment> implements PaymentDao {

    private static final String COLUMNS =
            "payment_id, share_id, from_user, to_user, amount, paid_at, status, confirmed_at, note, settlement_id";

    @Override
    public Payment findById(int id) throws DataAccessException {
        return queryOne("SELECT " + COLUMNS + " FROM payments WHERE payment_id = ?", "Could not load the payment.", id);
    }

    @Override
    public int insert(Payment p) throws DataAccessException {
        return withConnection("Could not save the payment.", c -> insert(c, p));
    }

    @Override
    public int insert(Connection c, Payment p) throws DataAccessException {
        return insert(c, "INSERT INTO payments (share_id, from_user, to_user, amount, paid_at, "
                        + "status, confirmed_at, note, settlement_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "Could not save the payment.",
                p.getShareId(), p.getFromUser(), p.getToUser(), p.getAmount(), p.getPaidAt(),
                p.getStatus(), p.getConfirmedAt(), p.getNote(), p.getSettlementId());
    }

    @Override
    public void update(Payment p) throws DataAccessException {
        withConnection("Could not update the payment.", c -> {
            update(c, p);
            return null;
        });
    }

    /** Saves the status, confirmed time and note. */
    @Override
    public void update(Connection c, Payment p) throws DataAccessException {
        update(c, "UPDATE payments SET status = ?, confirmed_at = ?, note = ? WHERE payment_id = ?",
                "Could not update the payment.", p.getStatus(), p.getConfirmedAt(), p.getNote(), p.getId());
    }

    @Override
    public void delete(int id) throws DataAccessException {
        update("DELETE FROM payments WHERE payment_id = ?", "Could not delete the payment.", id);
    }

    @Override
    public List<Payment> findWaitingFor(int receiverId) throws DataAccessException {
        return queryList("SELECT " + COLUMNS + " FROM payments WHERE to_user = ? AND status = ? "
                        + "AND settlement_id IS NULL ORDER BY paid_at, payment_id",
                "Could not load the payments.", receiverId, PaymentStatus.PENDING);
    }

    @Override
    public Payment findPendingForShare(int shareId) throws DataAccessException {
        return queryOne("SELECT " + COLUMNS + " FROM payments WHERE share_id = ? AND status = ? LIMIT 1",
                "Could not load the payment.", shareId, PaymentStatus.PENDING);
    }

    @Override
    public boolean existsConfirmedForExpense(int expenseId) throws DataAccessException {
        return exists("SELECT 1 FROM payments p JOIN expense_shares s ON s.share_id = p.share_id "
                        + "WHERE s.expense_id = ? AND p.status = ? LIMIT 1",
                "Could not check the payments.", expenseId, PaymentStatus.CONFIRMED);
    }

    @Override
    public List<Payment> findBySettlement(int settlementId) throws DataAccessException {
        return queryList("SELECT " + COLUMNS + " FROM payments WHERE settlement_id = ? ORDER BY payment_id",
                "Could not load the payments.", settlementId);
    }

    @Override
    public Set<Integer> findPendingShareIds(int householdId) throws DataAccessException {
        List<Integer> ids = query("SELECT p.share_id FROM payments p "
                        + "JOIN expense_shares s ON s.share_id = p.share_id "
                        + "JOIN expenses e ON e.expense_id = s.expense_id "
                        + "WHERE e.household_id = ? AND p.status = ?",
                rs -> rs.getInt(1), "Could not load the pending payments.", householdId, PaymentStatus.PENDING);
        return new HashSet<>(ids);
    }

    @Override
    protected Payment mapRow(ResultSet rs) throws SQLException {
        Payment p = new Payment(
                rs.getInt("payment_id"),
                rs.getInt("share_id"),
                rs.getInt("from_user"),
                rs.getInt("to_user"),
                rs.getBigDecimal("amount"),
                rs.getObject("paid_at", LocalDateTime.class),
                PaymentStatus.valueOf(rs.getString("status")),
                rs.getObject("confirmed_at", LocalDateTime.class), // null until confirmed
                rs.getString("note"));                              // null if no note
        int settlementId = rs.getInt("settlement_id");
        p.setSettlementId(rs.wasNull() ? null : settlementId);
        return p;
    }
}
