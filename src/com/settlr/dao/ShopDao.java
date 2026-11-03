package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * All SQL for the Shop: owned_items, xp_transactions, and the XP a user has earned.
 *
 * It doesn't implement Dao<T> because these rows have no id of their own (an owned item is
 * just "user X owns code Y"). Methods that take a Connection are for ShopService's
 * transactions, so paying and getting the item happen together or not at all.
 */
public class ShopDao {

    /** XP for each task point done on time / done late, and for each payment made on time. */
    public static final int XP_PER_POINT = 10;
    public static final int XP_PER_POINT_LATE = 5;
    public static final int XP_PER_PAYMENT = 20;

    /**
     * XP earned so far: every task finished (more when on time), and every payment the
     * user made that the other person confirmed and that was sent by the day after it was due.
     * Worked out from the tasks and payments themselves, so it can never drift out of step.
     */
    private static final String EARNED_SQL =
            "SELECT "
            + "(SELECT COALESCE(SUM(CASE a.status WHEN 'DONE' THEN t.points * " + XP_PER_POINT
            + " ELSE t.points * " + XP_PER_POINT_LATE + " END), 0) "
            + "   FROM task_assignments a JOIN tasks t ON t.task_id = a.task_id "
            + "  WHERE a.assignee_id = ? AND a.status IN ('DONE','DONE_LATE')) "
            + "+ (SELECT COUNT(*) * " + XP_PER_PAYMENT
            + "   FROM payments p "
            + "   JOIN expense_shares s ON s.share_id = p.share_id "
            + "   JOIN expenses e ON e.expense_id = s.expense_id "
            + "  WHERE p.from_user = ? AND p.status = 'CONFIRMED' "
            + "    AND p.paid_at < DATE_ADD(e.due_date, INTERVAL 1 DAY))";

    /** XP the user has right now: earned, minus what they spent, plus what they got back. */
    public int findBalance(int userId) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            return findBalance(c, userId);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load your XP.", ex);
        }
    }

    public int findBalance(Connection c, int userId) throws DataAccessException {
        String sql = "SELECT (" + EARNED_SQL + ") + "
                + "(SELECT COALESCE(SUM(amount), 0) FROM xp_transactions WHERE user_id = ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, userId);
            ps.setInt(3, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load your XP.", ex);
        }
    }

    /** The codes of everything the user owns (hats and pets), oldest first. */
    public Set<String> findOwnedCodes(int userId) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            return findOwnedCodes(c, userId);
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load your items.", ex);
        }
    }

    public Set<String> findOwnedCodes(Connection c, int userId) throws DataAccessException {
        String sql = "SELECT item_code FROM owned_items WHERE user_id = ? ORDER BY acquired_at, item_code";
        Set<String> codes = new LinkedHashSet<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    codes.add(rs.getString(1));
                }
            }
        } catch (SQLException ex) {
            throw new DataAccessException("Could not load your items.", ex);
        }
        return codes;
    }

    /**
     * Locks the user's row until the transaction ends, so two purchases at the same time
     * (e.g. a double click) can't both spend the same XP.
     */
    public void lockUser(Connection c, int userId) throws DataAccessException {
        try (PreparedStatement ps = c.prepareStatement("SELECT user_id FROM users WHERE user_id = ? FOR UPDATE")) {
            ps.setInt(1, userId);
            ps.executeQuery().close();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save your purchase.", ex);
        }
    }

    public void insertOwned(Connection c, int userId, String itemCode) throws DataAccessException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO owned_items (user_id, item_code) VALUES (?, ?)")) {
            ps.setInt(1, userId);
            ps.setString(2, itemCode);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save your purchase.", ex);
        }
    }

    /** Records XP spent (negative amount) or given back (positive). */
    public void insertTransaction(Connection c, int userId, int amount, String reason) throws DataAccessException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO xp_transactions (user_id, amount, reason) VALUES (?, ?, ?)")) {
            ps.setInt(1, userId);
            ps.setInt(2, amount);
            ps.setString(3, reason);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new DataAccessException("Could not save your purchase.", ex);
        }
    }
}
