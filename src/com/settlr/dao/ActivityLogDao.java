package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.ActivityLog;
import com.settlr.model.ActivityType;
import com.settlr.util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the activity_log table. Services call this class; nothing else touches these queries.
 *
 * <p>insert() leaves created_at out on purpose: the column's DEFAULT CURRENT_TIMESTAMP stamps
 * the time, so every event is timed by the same clock (the database's).
 */
public class ActivityLogDao implements Dao<ActivityLog> {

    private static final String COLUMNS = "log_id, household_id, user_id, action, message, created_at";

    /** Returns null when no row has this id. */
    @Override
    public ActivityLog findById(int id) throws DataAccessException {
        String sql = "SELECT " + COLUMNS + " FROM activity_log WHERE log_id = ?";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Couldn't load that activity. Please try again.", e);
        }
    }

    /** Saves the event and returns its new id. */
    @Override
    public int insert(ActivityLog log) throws DataAccessException {
        String sql = "INSERT INTO activity_log (household_id, user_id, action, message) VALUES (?, ?, ?, ?)";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, log.getHouseholdId());
            ps.setInt(2, log.getUserId());
            ps.setString(3, log.getType().name());
            ps.setString(4, log.getMessage());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new DataAccessException("Couldn't save that activity. Please try again.");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Couldn't save that activity. Please try again.", e);
        }
    }

    @Override
    public void update(ActivityLog log) throws DataAccessException {
        String sql = "UPDATE activity_log SET action = ?, message = ? WHERE log_id = ?";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, log.getType().name());
            ps.setString(2, log.getMessage());
            ps.setInt(3, log.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Couldn't update that activity. Please try again.", e);
        }
    }

    @Override
    public void delete(int id) throws DataAccessException {
        String sql = "DELETE FROM activity_log WHERE log_id = ?";
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Couldn't delete that activity. Please try again.", e);
        }
    }

    /**
     * The newest events of a household, newest first. log_id breaks ties between events logged
     * in the same second, so the order is always the same. The (household_id, created_at)
     * index in schema.sql makes this query fast.
     */
    public List<ActivityLog> findRecent(int householdId, int limit) throws DataAccessException {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1.");
        }
        String sql = "SELECT " + COLUMNS + " FROM activity_log WHERE household_id = ? "
                + "ORDER BY created_at DESC, log_id DESC LIMIT ?";
        List<ActivityLog> feed = new ArrayList<>();
        try (Connection con = DBConnection.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    feed.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Couldn't load the activity feed. Please try again.", e);
        }
        return feed;
    }

    /** One row to one object. Enum names match the SQL ENUM, so valueOf needs no translation. */
    private ActivityLog map(ResultSet rs) throws SQLException {
        return new ActivityLog(
                rs.getInt("log_id"),
                rs.getInt("household_id"),
                rs.getInt("user_id"),
                ActivityType.valueOf(rs.getString("action")),
                rs.getString("message"),
                rs.getObject("created_at", LocalDateTime.class));
    }
}
