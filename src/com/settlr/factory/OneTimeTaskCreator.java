package com.settlr.factory;

import com.settlr.model.OneTimeTask;
import com.settlr.model.Task;

// DESIGN PATTERN: Factory Method (Concrete Creator) - builds a OneTimeTask.
/** FACTORY METHOD pattern: a Concrete Creator. Builds OneTimeTask objects. */
public class OneTimeTaskCreator extends TaskCreator {

    @Override
    protected Task createTask(TaskRecord r) {
        return new OneTimeTask(r.getTitle(), r.getDescription(), r.getPoints(), r.getFirstDueAt(),
                r.getDefaultAssigneeId());
    }
}
