package com.settlr.dao.mysql;

import com.settlr.dao.TaskDao;
import com.settlr.exception.DataAccessException;
import com.settlr.factory.TaskCreator;
import com.settlr.factory.TaskRecord;
import com.settlr.model.Task;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** The tasks table in MySQL. */
public class MySqlTaskDao extends AbstractDao<Task> implements TaskDao {

    private static final String ONE_TIME = "ONE_TIME";
    private static final String RECURRING = "RECURRING";
    private static final String SELECT =
            "SELECT task_id, household_id, title, description, points, task_type, recurrence, "
          + "first_due_at, default_assignee_id, rotates, created_by, is_archived FROM tasks ";

    @Override
    public Task findById(int id) throws DataAccessException {
        return queryOne(SELECT + "WHERE task_id = ?", "Could not load the task.", id);
    }

    @Override
    public List<Task> findActiveByHousehold(int householdId) throws DataAccessException {
        return queryList(SELECT + "WHERE household_id = ? AND is_archived = FALSE ORDER BY task_id",
                "Could not load the tasks.", householdId);
    }

    @Override
    public List<Task> findAllActive() throws DataAccessException {
        return queryList(SELECT + "WHERE is_archived = FALSE ORDER BY task_id", "Could not load the tasks.");
    }

    @Override
    public int insert(Task task) throws DataAccessException {
        return withConnection("Could not save the task.", c -> insert(c, task));
    }

    @Override
    public int insert(Connection c, Task task) throws DataAccessException {
        return insert(c, "INSERT INTO tasks (household_id, title, description, points, task_type, "
                        + "recurrence, first_due_at, default_assignee_id, rotates, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "Could not save the task.",
                task.getHouseholdId(), task.getTitle(), task.getDescription(), task.getPoints(),
                task.repeats() ? RECURRING : ONE_TIME,
                task.getRecurrence(), // null for a one-time task
                task.getFirstDueAt(), task.getDefaultAssigneeId(),
                task.isRotates(), task.getCreatedBy());
    }

    @Override
    public void update(Task task) throws DataAccessException {
        withConnection("Could not update the task.", c -> {
            update(c, task);
            return null;
        });
    }

    /** task_type, household and creator never change, so they're left alone. */
    @Override
    public void update(Connection c, Task task) throws DataAccessException {
        update(c, "UPDATE tasks SET title = ?, description = ?, points = ?, recurrence = ?, "
                        + "first_due_at = ?, default_assignee_id = ?, rotates = ?, is_archived = ? "
                        + "WHERE task_id = ?",
                "Could not update the task.",
                task.getTitle(), task.getDescription(), task.getPoints(),
                task.getRecurrence(), task.getFirstDueAt(), task.getDefaultAssigneeId(),
                task.isRotates(), task.isArchived(), task.getId());
    }

    /** "Deleting" a task archives it: the row stays so past turns keep working. */
    @Override
    public void delete(int id) throws DataAccessException {
        update("UPDATE tasks SET is_archived = TRUE WHERE task_id = ?", "Could not delete the task.", id);
    }

    @Override
    public void delete(Connection c, int id) throws DataAccessException {
        update(c, "UPDATE tasks SET is_archived = TRUE WHERE task_id = ?", "Could not delete the task.", id);
    }

    /**
     * Reads the row into a plain TaskRecord; which class to build (OneTimeTask or RecurringTask)
     * is then decided by a TaskCreator (Factory Method).
     */
    @Override
    protected Task mapRow(ResultSet rs) throws SQLException {
        TaskRecord record = new TaskRecord(rs.getInt("task_id"), rs.getInt("household_id"),
                rs.getString("task_type"), rs.getString("title"), rs.getString("description"),
                rs.getInt("points"), rs.getString("recurrence"),
                rs.getObject("first_due_at", LocalDateTime.class), rs.getInt("default_assignee_id"),
                rs.getBoolean("rotates"), rs.getInt("created_by"), rs.getBoolean("is_archived"));
        return TaskCreator.forType(record.getType()).fromRecord(record);
    }
}
