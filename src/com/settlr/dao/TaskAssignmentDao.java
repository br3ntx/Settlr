package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
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
 * All SQL for the task_assignments table. Every read JOINs tasks and users so
 * the display fields (task title, points, assignee name) are filled without
 * extra queries. No business rules live here.
 */
public class TaskAssignmentDao implements Dao<TaskAssignment> {

    private static final String SELECT =
            "SELECT ta.assignment_id, ta.task_id, ta.assignee_id, ta.due_at, ta.completed_at, ta.status, "
          + "t.title, t.points, u.display_name "
          + "FROM task_assignments ta "
          + "JOIN tasks t ON t.task_id = ta.task_id "
          + "JOIN users u ON u.user_id = ta.assignee_id ";

    /** Returns null when no assignment has this id. */
    @Override
    public TaskAssignment findById(int id) throws DataAccessException {
        String sql = SELECT + "WHERE ta.assignment_id = ?";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the assignment.");
        }
    }

    /**
     * Assignments of one household with from <= due_at < to, earliest first.
     * "to" is exclusive so a week is [Monday 00:00, next Monday 00:00).
     */
    public List<TaskAssignment> findBetween(int householdId, LocalDateTime from, LocalDateTime to)
            throws DataAccessException {
        String sql = SELECT + "WHERE t.household_id = ? AND ta.due_at >= ? AND ta.due_at < ? "
                            + "ORDER BY ta.due_at, t.title";
        return findMany(sql, householdId, from, to);
    }

    /** PENDING assignments (any household) with due_at strictly before the cutoff. */
    public List<TaskAssignment> findPendingDueBefore(LocalDateTime cutoff) throws DataAccessException {
        String sql = SELECT + "WHERE ta.status = 'PENDING' AND ta.due_at < ? ORDER BY ta.due_at";
        return findMany(sql, null, cutoff, null);
    }

    /** The assignment with the latest due_at for a task, or null if it has none. */
    public TaskAssignment findLatestForTask(int taskId) throws DataAccessException {
        String sql = SELECT + "WHERE ta.task_id = ? ORDER BY ta.due_at DESC LIMIT 1";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the assignment.");
        }
    }

    @Override
    public int insert(TaskAssignment a) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            return insert(c, a);
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the assignment.");
        }
    }

    /** Plain insert inside the caller's transaction. Fails on a duplicate (task_id, due_at). */
    public int insert(Connection c, TaskAssignment a) throws DataAccessException {
        String sql = "INSERT INTO task_assignments (task_id, assignee_id, due_at, completed_at, status) "
                   + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, a.getTaskId());
            ps.setInt(2, a.getAssigneeId());
            ps.setObject(3, a.getDueAt());
            ps.setObject(4, a.getCompletedAt());
            ps.setString(5, a.getStatus().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new DataAccessException("Could not save the assignment.");
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the assignment.");
        }
    }

    public boolean insertIfAbsent(TaskAssignment a) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            return insertIfAbsent(c, a);
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the assignment.");
        }
    }

    /**
     * Inserts only if the task has no assignment at that due_at. Returns true
     * if a row was created (and sets its id), false if it already existed.
     *
     * Why not INSERT IGNORE: it would also hide real errors such as a bad
     * task id. This way only the duplicate case is skipped, which is what
     * makes running catch-up twice safe.
     */
    public boolean insertIfAbsent(Connection c, TaskAssignment a) throws DataAccessException {
        String sql = "INSERT INTO task_assignments (task_id, assignee_id, due_at, status) "
                   + "SELECT ?, ?, ?, ? FROM DUAL "
                   + "WHERE NOT EXISTS (SELECT 1 FROM task_assignments WHERE task_id = ? AND due_at = ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, a.getTaskId());
            ps.setInt(2, a.getAssigneeId());
            ps.setObject(3, a.getDueAt());
            ps.setString(4, a.getStatus().name());
            ps.setInt(5, a.getTaskId());
            ps.setObject(6, a.getDueAt());
            if (ps.executeUpdate() == 0) {
                return false;
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    a.setId(keys.getInt(1));
                }
            }
            return true;
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the assignment.");
        }
    }

    @Override
    public void update(TaskAssignment a) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            update(c, a);
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the assignment.");
        }
    }

    /** Updates assignee, completion time and status; task and due date never change. */
    public void update(Connection c, TaskAssignment a) throws DataAccessException {
        String sql = "UPDATE task_assignments SET assignee_id = ?, completed_at = ?, status = ? "
                   + "WHERE assignment_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, a.getAssigneeId());
            ps.setObject(2, a.getCompletedAt());
            ps.setString(3, a.getStatus().name());
            ps.setInt(4, a.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the assignment.");
        }
    }

    @Override
    public void delete(int id) throws DataAccessException {
        String sql = "DELETE FROM task_assignments WHERE assignment_id = ?";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the assignment.");
        }
    }

    /**
     * Deletes a task's PENDING assignments due after the given time, inside
     * the caller's transaction. Done, late and missed rows are never touched
     * because they are history (master doc 3.3).
     */
    public void deletePendingAfter(Connection c, int taskId, LocalDateTime after) throws DataAccessException {
        String sql = "DELETE FROM task_assignments WHERE task_id = ? AND status = 'PENDING' AND due_at > ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, taskId);
            ps.setObject(2, after);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not remove the pending assignments.");
        }
    }

    // ---- helpers -------------------------------------------------------

    /** Runs a SELECT whose parameters are (optional int, datetime, optional datetime). */
    private List<TaskAssignment> findMany(String sql, Integer householdId,
                                          LocalDateTime first, LocalDateTime second)
            throws DataAccessException {
        List<TaskAssignment> list = new ArrayList<>();
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (householdId != null) {
                ps.setInt(i++, householdId);
            }
            ps.setObject(i++, first);
            if (second != null) {
                ps.setObject(i, second);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the assignments.");
        }
    }

    private TaskAssignment mapRow(ResultSet rs) throws SQLException {
        TaskAssignment a = new TaskAssignment(
                rs.getInt("task_id"),
                rs.getInt("assignee_id"),
                rs.getObject("due_at", LocalDateTime.class));
        a.setId(rs.getInt("assignment_id"));
        a.setCompletedAt(rs.getObject("completed_at", LocalDateTime.class));
        a.setStatus(TaskStatus.valueOf(rs.getString("status")));
        a.setTaskTitle(rs.getString("title"));
        a.setTaskPoints(rs.getInt("points"));
        a.setAssigneeName(rs.getString("display_name"));
        return a;
    }
}
