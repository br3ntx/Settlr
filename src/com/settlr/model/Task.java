package com.settlr.model;

import java.time.LocalDateTime;

/**
 * A chore definition (what the chore is), not one due date. Each actual due
 * date is a TaskAssignment. Abstract so that OneTimeTask and RecurringTask
 * can each decide how their next due date is worked out (polymorphism).
 */
public abstract class Task {

    private int id;                 // 0 = not saved yet
    private int householdId;        // filled by TaskService from Session
    private String title;
    private String description;     // optional, may be null
    private int points;
    private LocalDateTime firstDueAt;
    private int defaultAssigneeId;
    private int createdBy;          // filled by TaskService from Session
    private boolean archived;

    /** Takes only what the task form knows; the service fills in the rest. */
    protected Task(String title, String description, int points,
                   LocalDateTime firstDueAt, int defaultAssigneeId) {
        setTitle(title);
        setDescription(description);
        setPoints(points);
        setFirstDueAt(firstDueAt);
        setDefaultAssigneeId(defaultAssigneeId);
    }

    /**
     * Returns the first due date strictly after {@code after}, or null if the
     * task has no further due date.
     */
    public abstract LocalDateTime getNextDueDate(LocalDateTime after);

    // Setters are final because the constructor calls them; a subclass must
    // not be able to override the validation.

    public int getId() { return id; }

    public final void setId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("Task id cannot be negative.");
        }
        this.id = id;
    }

    public int getHouseholdId() { return householdId; }

    public final void setHouseholdId(int householdId) {
        if (householdId <= 0) {
            throw new IllegalArgumentException("A task must belong to a household.");
        }
        this.householdId = householdId;
    }

    public String getTitle() { return title; }

    public final void setTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter a task title.");
        }
        if (title.trim().length() > 80) {
            throw new IllegalArgumentException("Task title can be at most 80 characters.");
        }
        this.title = title.trim();
    }

    public String getDescription() { return description; }

    public final void setDescription(String description) {
        // Blank means "no description" so the database stores NULL, not "".
        boolean blank = description == null || description.trim().isEmpty();
        this.description = blank ? null : description.trim();
    }

    public int getPoints() { return points; }

    public final void setPoints(int points) {
        if (points < 1 || points > 5) {
            throw new IllegalArgumentException("Points must be between 1 and 5.");
        }
        this.points = points;
    }

    public LocalDateTime getFirstDueAt() { return firstDueAt; }

    public final void setFirstDueAt(LocalDateTime firstDueAt) {
        if (firstDueAt == null) {
            throw new IllegalArgumentException("Please choose a first due date and time.");
        }
        this.firstDueAt = firstDueAt;
    }

    public int getDefaultAssigneeId() { return defaultAssigneeId; }

    public final void setDefaultAssigneeId(int defaultAssigneeId) {
        if (defaultAssigneeId <= 0) {
            throw new IllegalArgumentException("Please choose who the task is assigned to.");
        }
        this.defaultAssigneeId = defaultAssigneeId;
    }

    public int getCreatedBy() { return createdBy; }

    public final void setCreatedBy(int createdBy) {
        if (createdBy <= 0) {
            throw new IllegalArgumentException("A task needs a creator.");
        }
        this.createdBy = createdBy;
    }

    public boolean isArchived() { return archived; }

    public final void setArchived(boolean archived) { this.archived = archived; }
}
