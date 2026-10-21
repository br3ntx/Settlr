package com.settlr.model;

/**
 * Lifecycle state of one task assignment. Names match the SQL ENUM values
 * exactly so TaskStatus.valueOf(rs.getString("status")) works.
 */
public enum TaskStatus {
    PENDING, DONE, DONE_LATE, MISSED
}
