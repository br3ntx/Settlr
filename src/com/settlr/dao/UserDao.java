package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.User;
import com.settlr.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;

/** Reads and writes the users table. */
public class UserDao implements Dao<User> {

    private static final String COLUMNS = "user_id, username, display_name, password_hash, created_at";

    @Override
    public User findById(int id) throws DataAccessException {
        return findOne("SELECT " + COLUMNS + " FROM users WHERE user_id = ?", id,
                "Could not load the user.");
    }

    /**
     * The user with this username, or null if there isn't one.
     * Not case-sensitive ("Ana" finds "ana") because the column's collation ignores case.
     */
    public User findByUsername(String username) throws DataAccessException {
        return findOne("SELECT " + COLUMNS + " FROM users WHERE username = ?", username,
                "Could not look up that username.");
    }

    @Override
    public int insert(User user) throws DataAccessException {
        String sql = "INSERT INTO users (username, display_name, password_hash, created_at) VALUES (?, ?, ?, ?)";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getDisplayName());
            ps.setString(3, user.getPasswordHash());
            ps.setObject(4, user.getCreatedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                int id = keys.getInt(1);
                user.setId(id);
                return id;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not create the account.", e);
        }
    }

    @Override
    public void update(User user) throws DataAccessException {
        String sql = "UPDATE users SET username = ?, display_name = ?, password_hash = ? WHERE user_id = ?";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getDisplayName());
            ps.setString(3, user.getPasswordHash());
            ps.setInt(4, user.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the account.", e);
        }
    }

    /** Only works for users with no tasks, payments or memberships (the foreign keys block the rest). */
    @Override
    public void delete(int id) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE user_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the account.", e);
        }
    }

    // ---------- helpers ----------

    private User findOne(String sql, Object param, String errorMessage) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException(errorMessage, e);
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("user_id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("password_hash"),
                rs.getObject("created_at", LocalDateTime.class));
    }
}
