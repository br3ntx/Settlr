package com.settlr.model;

import java.time.LocalDateTime;

/**
 * Links a user to a household with a role. Removed members stay in the data
 * with active = false so history is preserved.
 */
public class Membership {
    private int householdId;
    private int userId;
    private Role role;
    private LocalDateTime joinedAt;
    private boolean active;

    public Membership(int householdId, int userId, Role role,
                      LocalDateTime joinedAt, boolean active) {
        setHouseholdId(householdId);
        setUserId(userId);
        setRole(role);
        setJoinedAt(joinedAt);
        setActive(active);
    }

    public int getHouseholdId() { return householdId; }

    public void setHouseholdId(int householdId) {
        if (householdId <= 0) throw new IllegalArgumentException("Household id must be positive.");
        this.householdId = householdId;
    }

    public int getUserId() { return userId; }

    public void setUserId(int userId) {
        if (userId <= 0) throw new IllegalArgumentException("User id must be positive.");
        this.userId = userId;
    }

    public Role getRole() { return role; }

    public void setRole(Role role) {
        if (role == null) throw new IllegalArgumentException("Role is required.");
        this.role = role;
    }

    public LocalDateTime getJoinedAt() { return joinedAt; }

    public void setJoinedAt(LocalDateTime joinedAt) {
        if (joinedAt == null) throw new IllegalArgumentException("Join time is required.");
        this.joinedAt = joinedAt;
    }

    public boolean isActive() { return active; }

    public void setActive(boolean active) { this.active = active; }
}