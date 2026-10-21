package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.Membership;
import com.settlr.model.Role;
import com.settlr.model.User;
import com.settlr.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the household_members table: who belongs to which household,
 * with which role.
 *
 * It doesn't implement Dao<Membership> because a membership has no single id;
 * it is identified by the pair (household_id, user_id).
 *
 * Removed members are never deleted, only marked inactive, so their old tasks,
 * payments and rankings still make sense (master doc 3.2).
 */
public class MembershipDao {

    /**
     * Everyone currently in the household, in the order they joined.
     * Task rotation and expense splitting rely on this order.
     */
    public List<User> findActiveMembers(int householdId) throws DataAccessException {
        String sql = "SELECT u.user_id, u.username, u.display_name, u.password_hash, u.created_at "
                + "FROM household_members hm "
                + "JOIN users u ON u.user_id = hm.user_id "
                + "WHERE hm.household_id = ? AND hm.is_active = TRUE "
                + "ORDER BY hm.joined_at, u.user_id";
        List<User> members = new ArrayList<>();
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    members.add(new User(
                            rs.getInt("user_id"),
                            rs.getString("username"),
                            rs.getString("display_name"),
                            rs.getString("password_hash"),
                            rs.getObject("created_at", LocalDateTime.class)));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the household members.", e);
        }
        return members;
    }

    /**
     * The household this user currently belongs to, or null if they haven't
     * created or joined one yet. A user is in one household at a time.
     */
    public Membership findActiveMembershipOf(int userId) throws DataAccessException {
        String sql = "SELECT household_id, user_id, role, joined_at, is_active "
                + "FROM household_members "
                + "WHERE user_id = ? AND is_active = TRUE "
                + "ORDER BY joined_at DESC LIMIT 1";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load your household membership.", e);
        }
    }

    /** One user's membership in one household (active or not), or null. */
    public Membership find(int householdId, int userId) throws DataAccessException {
        String sql = "SELECT household_id, user_id, role, joined_at, is_active "
                + "FROM household_members WHERE household_id = ? AND user_id = ?";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            ps.setInt(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the membership.", e);
        }
    }

    /**
     * Adds a user to a household. If they were in it before and got removed,
     * this re-activates them with the new role instead of failing on the
     * duplicate key.
     */
    public void insert(Membership m) throws DataAccessException {
        String sql = "INSERT INTO household_members (household_id, user_id, role, joined_at, is_active) "
                + "VALUES (?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE role = VALUES(role), is_active = VALUES(is_active)";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, m.getHouseholdId());
            ps.setInt(2, m.getUserId());
            ps.setString(3, m.getRole().name());
            ps.setObject(4, m.getJoinedAt());
            ps.setBoolean(5, m.isActive());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not add the member to the household.", e);
        }
    }

    /** Changes a member's role (e.g. making someone else Admin). */
    public void updateRole(int householdId, int userId, Role role) throws DataAccessException {
        runUpdate("UPDATE household_members SET role = ? WHERE household_id = ? AND user_id = ?",
                "Could not change the member's role.", role.name(), householdId, userId);
    }

    /** Removes a member by marking them inactive (their history is kept). */
    public void deactivate(int householdId, int userId) throws DataAccessException {
        runUpdate("UPDATE household_members SET is_active = FALSE WHERE household_id = ? AND user_id = ?",
                "Could not remove the member.", householdId, userId);
    }

    // ---------- helpers ----------

    private Membership mapRow(ResultSet rs) throws SQLException {
        return new Membership(
                rs.getInt("household_id"),
                rs.getInt("user_id"),
                Role.valueOf(rs.getString("role")),
                rs.getObject("joined_at", LocalDateTime.class),
                rs.getBoolean("is_active"));
    }

    private void runUpdate(String sql, String errorMessage, Object... params) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException(errorMessage, e);
        }
    }
}
