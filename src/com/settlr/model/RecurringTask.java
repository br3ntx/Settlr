package com.settlr.model;

import java.time.LocalDateTime;

/**
 * A chore that repeats DAILY, WEEKLY or MONTHLY from its first due date,
 * optionally rotating between household members.
 */
public class RecurringTask extends Task {

    private Recurrence recurrence;
    private boolean rotates;

    public RecurringTask(String title, String description, int points,
                         LocalDateTime firstDueAt, int defaultAssigneeId,
                         Recurrence recurrence, boolean rotates) {
        super(title, description, points, firstDueAt, defaultAssigneeId);
        setRecurrence(recurrence);
        this.rotates = rotates;
    }

    /**
     * Steps forward from firstDueAt until the date is after {@code after}.
     * Always starting from firstDueAt keeps the schedule on the same
     * time of day and, with the day 1-28 rule for MONTHLY, the same day.
     */
    @Override
    public LocalDateTime getNextDueDate(LocalDateTime after) {
        if (after == null) {
            throw new IllegalArgumentException("Date to look after is required.");
        }
        LocalDateTime due = getFirstDueAt();
        while (!due.isAfter(after)) {
            due = recurrence.next(due);
        }
        return due;
    }

    public Recurrence getRecurrence() { return recurrence; }

    public void setRecurrence(Recurrence recurrence) {
        if (recurrence == null) {
            throw new IllegalArgumentException("Please choose how often the task repeats.");
        }
        this.recurrence = recurrence;
    }

    public boolean isRotates() { return rotates; }

    public void setRotates(boolean rotates) { this.rotates = rotates; }
}
