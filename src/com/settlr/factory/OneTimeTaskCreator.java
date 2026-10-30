package com.settlr.factory;

import com.settlr.model.OneTimeTask;
import com.settlr.model.Task;

import java.sql.ResultSet;
import java.time.LocalDateTime;

/** FACTORY METHOD pattern: a Concrete Creator. Builds OneTimeTask objects. */
public class OneTimeTaskCreator extends TaskCreator {

    @Override
    protected Task createTask(ResultSet rs, String title, String description, int points,
                              LocalDateTime firstDueAt, int defaultAssigneeId) {
        return new OneTimeTask(title, description, points, firstDueAt, defaultAssigneeId);
    }
}
