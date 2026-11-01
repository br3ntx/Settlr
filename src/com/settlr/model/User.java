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
    private String avatarPath;      // null = show initials
    private PayMethod payMethod;    // null = not set
    private String payNumber;
    private String payName;

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

    // ---------- profile (all optional) ----------

    /** Path of the profile picture (e.g. uploads/avatars/user_3.png), or null for initials. */
    public String getAvatarPath() { return avatarPath; }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath == null || avatarPath.isBlank() ? null : avatarPath;
    }

    /** How this person wants to be paid, or null if they haven't said. */
    public PayMethod getPayMethod() { return payMethod; }

    public String getPayNumber() { return payNumber; }

    public String getPayName() { return payName; }

    /**
     * Sets "How to pay me" all at once. Null method clears it. Cash needs no number or name;
     * the other methods need both.
     */
    public void setPaymentInfo(PayMethod method, String number, String name) {
        String n = number == null ? "" : number.trim().replaceAll("\\s+", " ");
        String a = name == null ? "" : name.trim();
        if (method == null || !method.needsAccount()) {
            this.payMethod = method;
            this.payNumber = null;
            this.payName = null;
            return;
        }
        if (n.isEmpty()) {
            throw new IllegalArgumentException("Please enter your " + method.getNumberLabel().toLowerCase() + ".");
        }
        if (n.length() > 40) {
            throw new IllegalArgumentException("That number is too long (40 characters at most).");
        }
        if (method != PayMethod.BANK && !n.matches("\\+?[0-9 ]{10,16}")) {
            throw new IllegalArgumentException(method.getLabel() + " numbers are 11 digits, like 0917 123 4567.");
        }
        if (a.isEmpty() || a.length() > 60) {
            throw new IllegalArgumentException("Account name must be 1 to 60 characters.");
        }
        this.payMethod = method;
        this.payNumber = n;
        this.payName = a;
    }

    /** Copies every field from other (used to refresh the logged-in user after a save). */
    public void copyFrom(User other) {
        setUsername(other.getUsername());
        setDisplayName(other.getDisplayName());
        setPasswordHash(other.getPasswordHash());
        setCreatedAt(other.getCreatedAt());
        this.avatarPath = other.avatarPath;
        this.payMethod = other.payMethod;
        this.payNumber = other.payNumber;
        this.payName = other.payName;
    }
}
