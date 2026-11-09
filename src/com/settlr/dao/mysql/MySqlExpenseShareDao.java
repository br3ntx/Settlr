package com.settlr.dao.mysql;

import com.settlr.dao.ExpenseShareDao;
import com.settlr.exception.DataAccessException;
import com.settlr.model.ExpenseShare;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** The expense_shares table in MySQL. settled_at = NULL means unpaid. */
public class MySqlExpenseShareDao extends AbstractDao<ExpenseShare> implements ExpenseShareDao {

    private static final String COLUMNS = "share_id, expense_id, user_id, amount_owed, settled_at";
    private static final String SELECT_JOINED =
            "SELECT s.share_id, s.expense_id, s.user_id, s.amount_owed, s.settled_at "
          + "FROM expense_shares s JOIN expenses e ON e.expense_id = s.expense_id ";

    @Override
    public ExpenseShare findById(int id) throws DataAccessException {
        return queryOne("SELECT " + COLUMNS + " FROM expense_shares WHERE share_id = ?",
                "Could not load the share.", id);
    }

    @Override
    public int insert(ExpenseShare share) throws DataAccessException {
        return withConnection("Could not save the share.", c -> insert(c, share));
    }

    @Override
    public int insert(Connection c, ExpenseShare share) throws DataAccessException {
        return insert(c, "INSERT INTO expense_shares (expense_id, user_id, amount_owed, settled_at) VALUES (?, ?, ?, ?)",
                "Could not save the share.",
                share.getExpenseId(), share.getUserId(), share.getAmountOwed(), share.getSettledAt());
    }

    @Override
    public void update(ExpenseShare share) throws DataAccessException {
        withConnection("Could not update the share.", c -> {
            update(c, share);
            return null;
        });
    }

    @Override
    public void update(Connection c, ExpenseShare share) throws DataAccessException {
        update(c, "UPDATE expense_shares SET amount_owed = ?, settled_at = ? WHERE share_id = ?",
                "Could not update the share.", share.getAmountOwed(), share.getSettledAt(), share.getId());
    }

    @Override
    public void delete(int id) throws DataAccessException {
        update("DELETE FROM expense_shares WHERE share_id = ?", "Could not delete the share.", id);
    }

    @Override
    public List<ExpenseShare> findUnsettled(int debtorId, int creditorId) throws DataAccessException {
        return queryList(SELECT_JOINED + "WHERE s.user_id = ? AND e.paid_by = ? AND s.settled_at IS NULL "
                + "ORDER BY e.due_date, s.share_id", "Could not load the unpaid shares.", debtorId, creditorId);
    }

    @Override
    public List<ExpenseShare> findUnsettledPastDue(int userId, LocalDate today) throws DataAccessException {
        return queryList(SELECT_JOINED + "WHERE s.user_id = ? AND s.settled_at IS NULL AND e.due_date < ? "
                + "ORDER BY e.due_date, s.share_id", "Could not load the unpaid shares.", userId, today);
    }

    @Override
    public List<ExpenseShare> findByHousehold(int householdId) throws DataAccessException {
        return queryList(SELECT_JOINED + "WHERE e.household_id = ? ORDER BY s.expense_id, s.share_id",
                "Could not load the expense shares.", householdId);
    }

    @Override
    protected ExpenseShare mapRow(ResultSet rs) throws SQLException {
        return new ExpenseShare(
                rs.getInt("share_id"),
                rs.getInt("expense_id"),
                rs.getInt("user_id"),
                rs.getBigDecimal("amount_owed"),
                rs.getObject("settled_at", LocalDateTime.class)); // null = unpaid
    }
}
