package com.settlr.model;

import java.time.LocalDateTime;

/**
 * A Settlr account. Stores only the SHA-256 password hash, never the raw password.
 * Case-insensitive username uniqueness is a database/service concern, not checked here.
 */
public class User {
    private int id;
    private String username;
    private String displayName;
    private String passwordHash;
    private LocalDateTime createdAt;

    public User(int id, String username, String displayName,
                String passwordHash, LocalDateTime createdAt) {
        setId(id);
        setUsername(username);
        setDisplayName(displayName);
        setPasswordHash(passwordHash);
        setCreatedAt(createdAt);
    }

    public int getId() { return id; }

    /** 0 means "not saved yet"; saved users have positive IDs. */
    public void setId(int id) {
        if (id < 0) throw new IllegalArgumentException("User id cannot be negative.");
        this.id = id;
    }

    public String getUsername() { return username; }

    public void setUsername(String username) {
        if (username == null || username.isBlank())
            throw new IllegalArgumentException("Username is required.");
        if (username.length() < 3 || username.length() > 30)
            throw new IllegalArgumentException("Username must be 3 to 30 characters.");
        if (!username.matches("[A-Za-z0-9_]+"))
            throw new IllegalArgumentException("Username can only use letters, digits and underscore.");
        this.username = username;
    }

    public String getDisplayName() { return displayName; }

    public void setDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("Display name is required.");
        String trimmed = displayName.trim();
        if (trimmed.length() > 50)
            throw new IllegalArgumentException("Display name must be at most 50 characters.");
        this.displayName = trimmed;
    }

    public String getPasswordHash() { return passwordHash; }

    public void setPasswordHash(String passwordHash) {
        if (passwordHash == null || !passwordHash.matches("[0-9a-fA-F]{64}"))
            throw new IllegalArgumentException("Password hash must be exactly 64 hexadecimal characters.");
        this.passwordHash = passwordHash.toLowerCase();
    }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) {
        if (createdAt == null) throw new IllegalArgumentException("Creation time is required.");
        this.createdAt = createdAt;
    }
}