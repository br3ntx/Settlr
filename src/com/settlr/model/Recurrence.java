package com.settlr.model;

import java.time.LocalDateTime;

/**
 * How often a recurring task repeats. Names match the SQL ENUM values.
 * Each constant knows how to step one period forward.
 */
public enum Recurrence {
    DAILY, WEEKLY, MONTHLY;

    /**
     * Returns the due date one period after {@code from}.
     *
     * Why plusMonths is safe: TaskService rejects MONTHLY tasks whose first
     * due day is 29-31, so stepping month by month never has to shorten a day.
     */
    public LocalDateTime next(LocalDateTime from) {
        if (from == null) {
            throw new IllegalArgumentException("Date to step from is required.");
        }
        return switch (this) {
            case DAILY -> from.plusDays(1);
            case WEEKLY -> from.plusWeeks(1);
            case MONTHLY -> from.plusMonths(1);
        };
    }
}
