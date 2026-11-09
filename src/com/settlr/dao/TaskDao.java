package com.settlr.dao;

import com.settlr.exception.DataAccessException;
import com.settlr.model.Task;

import java.sql.Connection;
import java.util.List;

/**
 * The tasks table. Rows come back as OneTimeTask or RecurringTask, so callers only deal with Task.
 * delete() archives the task instead of removing the row, so past turns keep working.
 * The methods that take a Connection run inside the caller's Transaction.
 */
public interface TaskDao extends Dao<Task> {

    /** Non-archived tasks of one household. */
    List<Task> findActiveByHousehold(int householdId) throws DataAccessException;

    /** Non-archived tasks of every household (the startup catch-up runs before anyone logs in). */
    List<Task> findAllActive() throws DataAccessException;

    int insert(Connection c, Task task) throws DataAccessException;

    void update(Connection c, Task task) throws DataAccessException;

    void delete(Connection c, int id) throws DataAccessException;
}
