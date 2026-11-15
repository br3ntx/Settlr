package com.settlr.factory;

import com.settlr.model.Task;

/**
 * FACTORY METHOD pattern: the Creator.
 *
 * Turning a row of the tasks table (a TaskRecord) into an object has two parts:
 *   1. steps that are the same for every task (id, household, creator, archived), done here in fromRecord();
 *   2. choosing WHICH Task subclass to build, left to the abstract factory method createTask().
 *
 * Each subclass (OneTimeTaskCreator, RecurringTaskCreator) overrides createTask() and
 * decides the concrete class. fromRecord() never knows which kind of task it got back;
 * it only works with the Task parent type. A new kind of task means a new creator
 * subclass, with no change to this class.
 */
public abstract class TaskCreator {

    /**
     * The factory method. Builds the right Task subclass from the record,
     * using any extra values that only that kind of task has.
     */
    protected abstract Task createTask(TaskRecord record);

    /** Builds a complete Task from one record. Uses the factory method for the part that differs. */
    public final Task fromRecord(TaskRecord record) {
        Task task = createTask(record);
        task.setId(record.getId());
        task.setHouseholdId(record.getHouseholdId());
        task.setCreatedBy(record.getCreatedBy());
        task.setArchived(record.isArchived());
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
