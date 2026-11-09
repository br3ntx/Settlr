package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.TaskAssignment;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The task_assignments table: each turn of a task, with who it's for. Reads fill in the task's
 * title and points and the assignee's name too.
 */
public interface TaskAssignmentDao extends Dao<TaskAssignment> {

    /** Turns of one household with from <= due < to, earliest first ("to" is not included). */
    List<TaskAssignment> findBetween(int householdId, LocalDateTime from, LocalDateTime to)
            throws DataAccessException;

    /** PENDING turns (any household) due before the cutoff. */
    List<TaskAssignment> findPendingDueBefore(LocalDateTime cutoff) throws DataAccessException;

    /** The task's turn with the latest due date, or null if it has none. */
    TaskAssignment findLatestForTask(int taskId) throws DataAccessException;

    /** Inside the caller's transaction. Fails on a duplicate (task, due date). */
    int insert(Connection c, TaskAssignment a) throws DataAccessException;

    /** Saves the turn unless the task already has one at that due date. True if it was saved. */
    boolean insertIfAbsent(TaskAssignment a) throws DataAccessException;

    boolean insertIfAbsent(Connection c, TaskAssignment a) throws DataAccessException;

    /** Updates assignee, completion time and status. */
    void update(Connection c, TaskAssignment a) throws DataAccessException;

    /** Deletes a task's PENDING turns due after the given time (done ones are history). */
    void deletePendingAfter(Connection c, int taskId, LocalDateTime after) throws DataAccessException;
}
