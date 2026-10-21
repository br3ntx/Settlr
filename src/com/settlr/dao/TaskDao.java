package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.OneTimeTask;
import com.settlr.model.Recurrence;
import com.settlr.model.RecurringTask;
import com.settlr.model.Task;
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
 * All SQL for the tasks table. Rows become OneTimeTask or RecurringTask
 * objects depending on task_type, so callers only ever deal with Task
 * (polymorphism). No business rules live here.
 *
 * Methods that take a Connection exist so a service can group several DAO
 * calls into one transaction; the versions without it open their own.
 */
public class TaskDao implements Dao<Task> {

    private static final String ONE_TIME = "ONE_TIME";
    private static final String RECURRING = "RECURRING";

    private static final String SELECT =
            "SELECT task_id, household_id, title, description, points, task_type, recurrence, "
          + "first_due_at, default_assignee_id, rotates, created_by, is_archived FROM tasks ";

    /** Returns null when no task has this id. */
    @Override
    public Task findById(int id) throws DataAccessException {
        String sql = SELECT + "WHERE task_id = ?";
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the task.");
        }
    }

    /** Non-archived tasks of one household. */
    public List<Task> findActiveByHousehold(int householdId) throws DataAccessException {
        return findMany(SELECT + "WHERE household_id = ? AND is_archived = FALSE ORDER BY task_id", householdId);
    }

    /**
     * Non-archived tasks of every household. Catch-up runs at startup, before
     * anyone is logged in, so it cannot ask for one household.
     */
    public List<Task> findAllActive() throws DataAccessException {
        return findMany(SELECT + "WHERE is_archived = FALSE ORDER BY task_id", null);
    }

    @Override
    public int insert(Task task) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            return insert(c, task);
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the task.");
        }
    }

    /** Same as insert(Task) but inside the caller's transaction. */
    public int insert(Connection c, Task task) throws DataAccessException {
        String sql = "INSERT INTO tasks (household_id, title, description, points, task_type, "
                   + "recurrence, first_due_at, default_assignee_id, rotates, created_by) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            boolean recurring = task instanceof RecurringTask;
            ps.setInt(1, task.getHouseholdId());
            ps.setString(2, task.getTitle());
            ps.setString(3, task.getDescription());
            ps.setInt(4, task.getPoints());
            ps.setString(5, recurring ? RECURRING : ONE_TIME);
            ps.setString(6, recurring ? ((RecurringTask) task).getRecurrence().name() : null);
            ps.setObject(7, task.getFirstDueAt());
            ps.setInt(8, task.getDefaultAssigneeId());
            ps.setBoolean(9, recurring && ((RecurringTask) task).isRotates());
            ps.setInt(10, task.getCreatedBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
            throw new DataAccessException("Could not save the task.");
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the task.");
        }
    }

    @Override
    public void update(Task task) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            update(c, task);
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the task.");
        }
    }

    /**
     * Updates the editable columns. task_type, household_id and created_by are
     * left alone: a task never changes type, household or creator.
     */
    public void update(Connection c, Task task) throws DataAccessException {
        String sql = "UPDATE tasks SET title = ?, description = ?, points = ?, recurrence = ?, "
                   + "first_due_at = ?, default_assignee_id = ?, rotates = ?, is_archived = ? "
                   + "WHERE task_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            boolean recurring = task instanceof RecurringTask;
            ps.setString(1, task.getTitle());
            ps.setString(2, task.getDescription());
            ps.setInt(3, task.getPoints());
            ps.setString(4, recurring ? ((RecurringTask) task).getRecurrence().name() : null);
            ps.setObject(5, task.getFirstDueAt());
            ps.setInt(6, task.getDefaultAssigneeId());
            ps.setBoolean(7, recurring && ((RecurringTask) task).isRotates());
            ps.setBoolean(8, task.isArchived());
            ps.setInt(9, task.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the task.");
        }
    }

    /**
     * "Deleting" a task archives it (master doc 3.3): the row stays so past
     * assignments and rankings keep working.
     */
    @Override
    public void delete(int id) throws DataAccessException {
        try (Connection c = DBConnection.getInstance().getConnection()) {
            delete(c, id);
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the task.");
        }
    }

    /** Archive inside the caller's transaction. */
    public void delete(Connection c, int id) throws DataAccessException {
        String sql = "UPDATE tasks SET is_archived = TRUE WHERE task_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the task.");
        }
    }

    // ---- helpers -------------------------------------------------------

    /** Runs a SELECT with an optional single int parameter (null = none). */
    private List<Task> findMany(String sql, Integer param) throws DataAccessException {
        List<Task> tasks = new ArrayList<>();
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            if (param != null) {
                ps.setInt(1, param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tasks.add(mapRow(rs));
                }
            }
            return tasks;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the tasks.");
        }
    }

    /** Builds the right subclass from task_type. */
    private Task mapRow(ResultSet rs) throws SQLException {
        String title = rs.getString("title");
        String description = rs.getString("description");
        int points = rs.getInt("points");
        LocalDateTime firstDueAt = rs.getObject("first_due_at", LocalDateTime.class);
        int assigneeId = rs.getInt("default_assignee_id");

        Task task;
        if (RECURRING.equals(rs.getString("task_type"))) {
            Recurrence recurrence = Recurrence.valueOf(rs.getString("recurrence"));
            task = new RecurringTask(title, description, points, firstDueAt, assigneeId,
                                     recurrence, rs.getBoolean("rotates"));
        } else {
            task = new OneTimeTask(title, description, points, firstDueAt, assigneeId);
        }
        task.setId(rs.getInt("task_id"));
        task.setHouseholdId(rs.getInt("household_id"));
        task.setCreatedBy(rs.getInt("created_by"));
        task.setArchived(rs.getBoolean("is_archived"));
        return task;
    }
}
