package com.settlr.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A chore definition (what the chore is), not one due date. Each actual due
 * date is a TaskAssignment. Abstract so that OneTimeTask and RecurringTask
 * can each decide how their next due date is worked out (polymorphism).
 *
 * The services never check which kind of task they have (no instanceof): they ask the task
 * itself, e.g. getNextDueDate(), nextAssignee() or checkSchedule(), and each subclass answers
 * in its own way.
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

    /** True if the task has more than one due date (it repeats). */
    public abstract boolean repeats();

    /** How often the task repeats, or null if it doesn't (saved as NULL in the recurrence column). */
    public abstract Recurrence getRecurrence();

    /** True if the turns pass from member to member. Only a repeating task can rotate. */
    public boolean isRotates() {
        return false;
    }

    /**
     * Who does the turn after previousAssignee's, given the active members in join order.
     * Here: always the default assignee (or the first member, if they left). RecurringTask
     * overrides this to pass the turn to the next member when it rotates.
     */
    public int nextAssignee(int previousAssignee, List<User> members) {
        if (members == null || members.isEmpty()) {
            throw new IllegalArgumentException("There is nobody to give the task to.");
        }
        for (User member : members) {
            if (member.getId() == defaultAssigneeId) {
                return defaultAssigneeId;
            }
        }
        return members.get(0).getId();
    }

    /**
     * Throws IllegalArgumentException if the schedule can't work. Nothing to check for a
     * one-time task; RecurringTask checks the day of the month for monthly tasks.
     */
    public void checkSchedule() {
        // a one-time task can be due on any date
    }

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
