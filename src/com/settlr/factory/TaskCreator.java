package com.settlr.factory;

import com.settlr.model.Task;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * FACTORY METHOD pattern: the Creator.
 *
 * Turning a row of the tasks table into an object has two parts:
 *   1. steps that are the same for every task (id, household, creator, archived), done here in fromRow();
 *   2. choosing WHICH Task subclass to build, left to the abstract factory method createTask().
 *
 * Each subclass (OneTimeTaskCreator, RecurringTaskCreator) overrides createTask() and
 * decides the concrete class. fromRow() never knows which kind of task it got back;
 * it only works with the Task parent type. A new kind of task means a new creator
 * subclass, with no change to this class.
 */
public abstract class TaskCreator {

    /**
     * The factory method. Builds the right Task subclass from the shared fields,
     * reading any extra columns that only that kind of task has.
     */
    protected abstract Task createTask(ResultSet rs, String title, String description, int points,
                                       LocalDateTime firstDueAt, int defaultAssigneeId) throws SQLException;

    /** Builds a complete Task from one row. Uses the factory method for the part that differs. */
    public final Task fromRow(ResultSet rs) throws SQLException {
        Task task = createTask(rs,
                rs.getString("title"),
                rs.getString("description"),
                rs.getInt("points"),
                rs.getObject("first_due_at", LocalDateTime.class),
                rs.getInt("default_assignee_id"));
        task.setId(rs.getInt("task_id"));
        task.setHouseholdId(rs.getInt("household_id"));
        task.setCreatedBy(rs.getInt("created_by"));
        task.setArchived(rs.getBoolean("is_archived"));
        return task;
    }

    /** The creator for a task_type value from the database ("ONE_TIME" or "RECURRING"). */
    public static TaskCreator forType(String taskType) {
        if ("RECURRING".equals(taskType)) {
            return new RecurringTaskCreator();
        }
        if ("ONE_TIME".equals(taskType)) {
            return new OneTimeTaskCreator();
        }
        throw new IllegalArgumentException("Unknown task type: " + taskType);
    }
}
