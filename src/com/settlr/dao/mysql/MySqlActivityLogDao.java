package com.settlr.dao.mysql;

import com.settlr.dao.ActivityLogDao;
import com.settlr.exception.DataAccessException;
import com.settlr.model.ActivityLog;
import com.settlr.model.ActivityType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The activity_log table in MySQL. insert() leaves created_at out on purpose: the column's
 * DEFAULT CURRENT_TIMESTAMP stamps it, so every event is timed by the database's clock.
 */
public class MySqlActivityLogDao extends AbstractDao<ActivityLog> implements ActivityLogDao {

    private static final String COLUMNS = "log_id, household_id, user_id, action, message, created_at";

    @Override
    public ActivityLog findById(int id) throws DataAccessException {
        return queryOne("SELECT " + COLUMNS + " FROM activity_log WHERE log_id = ?",
                "Couldn't load that activity. Please try again.", id);
    }

    @Override
    public int insert(ActivityLog log) throws DataAccessException {
        return insert("INSERT INTO activity_log (household_id, user_id, action, message) VALUES (?, ?, ?, ?)",
                "Couldn't save that activity. Please try again.",
                log.getHouseholdId(), log.getUserId(), log.getType(), log.getMessage());
    }

    @Override
    public void update(ActivityLog log) throws DataAccessException {
        update("UPDATE activity_log SET action = ?, message = ? WHERE log_id = ?",
                "Couldn't update that activity. Please try again.", log.getType(), log.getMessage(), log.getId());
    }

    @Override
    public void delete(int id) throws DataAccessException {
        update("DELETE FROM activity_log WHERE log_id = ?", "Couldn't delete that activity. Please try again.", id);
    }

    /** log_id breaks ties between events logged in the same second, so the order never changes. */
    @Override
    public List<ActivityLog> findRecent(int householdId, int limit) throws DataAccessException {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1.");
        }
        return queryList("SELECT " + COLUMNS + " FROM activity_log WHERE household_id = ? "
                        + "ORDER BY created_at DESC, log_id DESC LIMIT ?",
                "Couldn't load the activity feed. Please try again.", householdId, limit);
    }

    @Override
    protected ActivityLog mapRow(ResultSet rs) throws SQLException {
        return new ActivityLog(
                rs.getInt("log_id"),
                rs.getInt("household_id"),
                rs.getInt("user_id"),
                ActivityType.valueOf(rs.getString("action")),
                rs.getString("message"),
                rs.getObject("created_at", LocalDateTime.class));
    }
}
