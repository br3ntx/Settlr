package com.settlr.factory;

import java.time.LocalDateTime;

/**
 * One row of the tasks table as plain Java values, with no JDBC types in it.
 * The DAO reads the row into a TaskRecord; a TaskCreator turns it into a Task.
 * Keeping ResultSet out of here means the factory works the same with any kind of storage.
 */
public final class TaskRecord {

    private final int id;
    private final int householdId;
    private final String type;          // "ONE_TIME" or "RECURRING"
    private final String title;
    private final String description;
    private final int points;
    private final String recurrence;    // "DAILY", "WEEKLY", "MONTHLY", or null for a one-time task
    private final LocalDateTime firstDueAt;
    private final int defaultAssigneeId;
    private final boolean rotates;
    private final int createdBy;
    private final boolean archived;

    public TaskRecord(int id, int householdId, String type, String title, String description, int points,
                      String recurrence, LocalDateTime firstDueAt, int defaultAssigneeId, boolean rotates,
                      int createdBy, boolean archived) {
        this.id = id;
        this.householdId = householdId;
        this.type = type;
        this.title = title;
        this.description = description;
        this.points = points;
        this.recurrence = recurrence;
        this.firstDueAt = firstDueAt;
        this.defaultAssigneeId = defaultAssigneeId;
        this.rotates = rotates;
        this.createdBy = createdBy;
        this.archived = archived;
    }

    public int getId() { return id; }
    public int getHouseholdId() { return householdId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getPoints() { return points; }
    public String getRecurrence() { return recurrence; }
    public LocalDateTime getFirstDueAt() { return firstDueAt; }
    public int getDefaultAssigneeId() { return defaultAssigneeId; }
    public boolean isRotates() { return rotates; }
    public int getCreatedBy() { return createdBy; }
    public boolean isArchived() { return archived; }
}
