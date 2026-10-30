package com.settlr.factory;

import com.settlr.model.Recurrence;
import com.settlr.model.RecurringTask;
import com.settlr.model.Task;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * FACTORY METHOD pattern: a Concrete Creator. Builds RecurringTask objects,
 * reading the two columns only recurring tasks use (recurrence and rotates).
 */
public class RecurringTaskCreator extends TaskCreator {

    @Override
    protected Task createTask(ResultSet rs, String title, String description, int points,
                              LocalDateTime firstDueAt, int defaultAssigneeId) throws SQLException {
        return new RecurringTask(title, description, points, firstDueAt, defaultAssigneeId,
                Recurrence.valueOf(rs.getString("recurrence")), rs.getBoolean("rotates"));
    }
}
