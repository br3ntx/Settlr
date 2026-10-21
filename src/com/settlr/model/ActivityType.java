package com.settlr.model;

/**
 * The kinds of events shown in the activity feed.
 *
 * <p>The names match the activity_log.action SQL ENUM letter for letter, so
 * ActivityType.valueOf(rs.getString("action")) works with no translation table.
 */
public enum ActivityType {
    TASK_DONE,
    TASK_LATE,
    TASK_MISSED,
    EXPENSE_ADDED,
    PAYMENT_CONFIRMED,
    SWAP_ACCEPTED,
    MEMBER_JOINED
}
