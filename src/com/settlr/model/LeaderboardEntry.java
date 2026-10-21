package com.settlr.model;

/**
 * One row of a leaderboard: who it is, their raw value, their rank, and the text to show.
 *
 * <p>Why userId + displayName instead of a whole User: the ranking SQL only reads those two
 * columns, and building a full User would need a password hash we never select.
 * Why a separate label: the value is a double for sorting, while the label is what people
 * read ("12 pts", "85%", "₱450.00"). This is the only place a double is allowed for money-like values.
 */
public class LeaderboardEntry {

    private int userId;
    private String displayName;
    private double value;
    private int rank;
    private String label;

    public LeaderboardEntry(int userId, String displayName, double value, int rank, String label) {
        setUserId(userId);
        setDisplayName(displayName);
        setValue(value);
        setRank(rank);
        setLabel(label);
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Display name cannot be empty.");
        }
        this.displayName = displayName;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Value must be a real number.");
        }
        this.value = value;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        if (rank < 1) {
            throw new IllegalArgumentException("Rank starts at 1.");
        }
        this.rank = rank;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        if (label == null) {
            throw new IllegalArgumentException("Label cannot be null.");
        }
        this.label = label;
    }
}
