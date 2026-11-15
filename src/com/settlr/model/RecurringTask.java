package com.settlr.model;

import java.time.LocalDateTime;
import java.util.List;

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

    @Override
    public boolean repeats() {
        return true;
    }

    /**
     * A rotating task goes to the next active member after the previous assignee, in join order
     * (wrapping around); if that person left, it starts again from the first member.
     * A non-rotating one stays with the default assignee, like any task.
     */
    @Override
    public int nextAssignee(int previousAssignee, List<User> members) {
        if (!rotates) {
            return super.nextAssignee(previousAssignee, members);
        }
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("There is nobody to give the task to.");
        }
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).getId() == previousAssignee) {
                return members.get((i + 1) % members.size()).getId();
            }
        }
        return members.get(0).getId();
    }

    /**
     * Monthly tasks must start on day 1 to 28, so every month has that day
     * (the same rule as monthly bills); Recurrence.next relies on it.
     */
    @Override
    public void checkSchedule() {
        if (recurrence == Recurrence.MONTHLY && getFirstDueAt().getDayOfMonth() > 28) {
            throw new IllegalArgumentException("Monthly tasks must start on day 1 to 28 of the month.");
        }
    }

    @Override
    public Recurrence getRecurrence() { return recurrence; }

    public void setRecurrence(Recurrence recurrence) {
        if (recurrence == null) {
            throw new IllegalArgumentException("Please choose how often the task repeats.");
        }
        this.recurrence = recurrence;
    }

    @Override
    public boolean isRotates() { return rotates; }

    public void setRotates(boolean rotates) { this.rotates = rotates; }
}
