package com.settlr.factory;

import com.settlr.model.Recurrence;
import com.settlr.model.RecurringTask;
import com.settlr.model.Task;

// DESIGN PATTERN: Factory Method (Concrete Creator) - builds a RecurringTask.
/**
 * FACTORY METHOD pattern: a Concrete Creator. Builds RecurringTask objects,
 * using the two values only recurring tasks have (recurrence and rotates).
 */
public class RecurringTaskCreator extends TaskCreator {

    @Override
    protected Task createTask(TaskRecord r) {
        return new RecurringTask(r.getTitle(), r.getDescription(), r.getPoints(), r.getFirstDueAt(),
                r.getDefaultAssigneeId(), Recurrence.valueOf(r.getRecurrence()), r.isRotates());
    }
}
