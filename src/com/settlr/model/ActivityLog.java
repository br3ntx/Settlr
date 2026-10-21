package com.settlr.model;

import java.time.LocalDateTime;

/**
 * One line of the activity feed. Mirrors a row of the activity_log table.
 *
 * <p>The message is written by whoever logs the event ("Ana completed Wash Dishes (2 hours late)")
 * and is stored as finished text, so the feed never has to rebuild a sentence from other tables.
 */
public class ActivityLog {

    /** activity_log.message is VARCHAR(255). */
    public static final int MAX_MESSAGE_LENGTH = 255;

    private int id;
    private int householdId;
    private int userId;
    private ActivityType type;
    private String message;
    private LocalDateTime createdAt;

    /** For a new event that is not saved yet: the database fills in the id and the time. */
    public ActivityLog(int householdId, int userId, ActivityType type, String message) {
        this(0, householdId, userId, type, message, null);
    }

    /** For a row loaded from the database. */
    public ActivityLog(int id, int householdId, int userId, ActivityType type, String message,
                       LocalDateTime createdAt) {
        setId(id);
        setHouseholdId(householdId);
        setUserId(userId);
        setType(type);
        setMessage(message);
        setCreatedAt(createdAt);
    }

    public int getId() {
        return id;
    }

    /** 0 means "not saved yet". */
    public void setId(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("Log id cannot be negative.");
        }
        this.id = id;
    }

    public int getHouseholdId() {
        return householdId;
    }

    public void setHouseholdId(int householdId) {
        if (householdId <= 0) {
            throw new IllegalArgumentException("Household id must be positive.");
        }
        this.householdId = householdId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("User id must be positive.");
        }
        this.userId = userId;
    }

    public ActivityType getType() {
        return type;
    }

    public void setType(ActivityType type) {
        if (type == null) {
            throw new IllegalArgumentException("Activity type is required.");
        }
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Activity message cannot be empty.");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Activity message is longer than " + MAX_MESSAGE_LENGTH + " characters.");
        }
        this.message = message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** May be null for an event that has not been saved yet. */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
