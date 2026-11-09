package com.settlr.dao.mysql;

import com.settlr.dao.TaskAssignmentDao;
import com.settlr.exception.DataAccessException;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

/** The task_assignments table in MySQL. Every read joins tasks and users for the display fields. */
public class MySqlTaskAssignmentDao extends AbstractDao<TaskAssignment> implements TaskAssignmentDao {

    private static final String SELECT =
            "SELECT ta.assignment_id, ta.task_id, ta.assignee_id, ta.due_at, ta.completed_at, ta.status, "
          + "t.title, t.points, u.display_name "
          + "FROM task_assignments ta "
          + "JOIN tasks t ON t.task_id = ta.task_id "
          + "JOIN users u ON u.user_id = ta.assignee_id ";

    @Override
    public TaskAssignment findById(int id) throws DataAccessException {
        return queryOne(SELECT + "WHERE ta.assignment_id = ?", "Could not load the assignment.", id);
    }

    @Override
    public List<TaskAssignment> findBetween(int householdId, LocalDateTime from, LocalDateTime to)
            throws DataAccessException {
        return queryList(SELECT + "WHERE t.household_id = ? AND ta.due_at >= ? AND ta.due_at < ? "
                + "ORDER BY ta.due_at, t.title", "Could not load the assignments.", householdId, from, to);
    }

    @Override
    public List<TaskAssignment> findPendingDueBefore(LocalDateTime cutoff) throws DataAccessException {
        return queryList(SELECT + "WHERE ta.status = 'PENDING' AND ta.due_at < ? ORDER BY ta.due_at",
                "Could not load the assignments.", cutoff);
    }

    @Override
    public TaskAssignment findLatestForTask(int taskId) throws DataAccessException {
        return queryOne(SELECT + "WHERE ta.task_id = ? ORDER BY ta.due_at DESC LIMIT 1",
                "Could not load the assignment.", taskId);
    }

    @Override
    public int insert(TaskAssignment a) throws DataAccessException {
        return withConnection("Could not save the assignment.", c -> insert(c, a));
    }

    @Override
    public int insert(Connection c, TaskAssignment a) throws DataAccessException {
        return insert(c, "INSERT INTO task_assignments (task_id, assignee_id, due_at, completed_at, status) "
                        + "VALUES (?, ?, ?, ?, ?)",
                "Could not save the assignment.",
                a.getTaskId(), a.getAssigneeId(), a.getDueAt(), a.getCompletedAt(), a.getStatus());
    }

    @Override
    public boolean insertIfAbsent(TaskAssignment a) throws DataAccessException {
        return withConnection("Could not save the assignment.", c -> insertIfAbsent(c, a));
    }

    /**
     * Inserts only if the task has no turn at that due date, and sets the new id.
     * Why not INSERT IGNORE: it would also hide real errors such as a bad task id. This way only
     * the duplicate case is skipped, which is what makes running catch-up twice safe.
     */
    @Override
    public boolean insertIfAbsent(Connection c, TaskAssignment a) throws DataAccessException {
        String sql = "INSERT INTO task_assignments (task_id, assignee_id, due_at, status) "
                   + "SELECT ?, ?, ?, ? FROM DUAL "
                   + "WHERE NOT EXISTS (SELECT 1 FROM task_assignments WHERE task_id = ? AND due_at = ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, a.getTaskId(), a.getAssigneeId(), a.getDueAt(), a.getStatus(), a.getTaskId(), a.getDueAt());
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
            throw new DataAccessException("Could not save the assignment.", e);
        }
    }

    @Override
    public void update(TaskAssignment a) throws DataAccessException {
        withConnection("Could not update the assignment.", c -> {
            update(c, a);
            return null;
        });
    }

    /** Updates assignee, completion time and status; the task and due date never change. */
    @Override
    public void update(Connection c, TaskAssignment a) throws DataAccessException {
        update(c, "UPDATE task_assignments SET assignee_id = ?, completed_at = ?, status = ? WHERE assignment_id = ?",
                "Could not update the assignment.", a.getAssigneeId(), a.getCompletedAt(), a.getStatus(), a.getId());
    }

    @Override
    public void delete(int id) throws DataAccessException {
        update("DELETE FROM task_assignments WHERE assignment_id = ?", "Could not delete the assignment.", id);
    }

    @Override
    public void deletePendingAfter(Connection c, int taskId, LocalDateTime after) throws DataAccessException {
        update(c, "DELETE FROM task_assignments WHERE task_id = ? AND status = 'PENDING' AND due_at > ?",
                "Could not remove the pending assignments.", taskId, after);
    }

    @Override
    protected TaskAssignment mapRow(ResultSet rs) throws SQLException {
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
