package com.settlr.model;

import java.time.LocalDateTime;

/**
 * A chore that happens once, on its first due date.
 */
public class OneTimeTask extends Task {

    public OneTimeTask(String title, String description, int points,
                       LocalDateTime firstDueAt, int defaultAssigneeId) {
        super(title, description, points, firstDueAt, defaultAssigneeId);
    }

    /** The only due date is firstDueAt; once it has passed there is none. */
    @Override
    public LocalDateTime getNextDueDate(LocalDateTime after) {
        if (after == null) {
            throw new IllegalArgumentException("Date to look after is required.");
        }
        return getFirstDueAt().isAfter(after) ? getFirstDueAt() : null;
    }
}
