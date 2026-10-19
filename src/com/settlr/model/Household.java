package com.settlr.model;

/**
 * A group of people sharing a room or house, identified by a 6-character invite code.
 * Database operations and household creation logic live elsewhere (DAO/service).
 */
public class Household {
    /** Allowed invite code characters (no 0/O or 1/I). */
    public static final String INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int MAX_NAME_LENGTH = 60;
    public static final int INVITE_CODE_LENGTH = 6;

    private int id;
    private String name;
    private String inviteCode;
    private int createdBy;
    private boolean hideShameRanks;

    public Household(int id, String name, String inviteCode,
                     int createdBy, boolean hideShameRanks) {
        setId(id);
        setName(name);
        setInviteCode(inviteCode);
        setCreatedBy(createdBy);
        setHideShameRanks(hideShameRanks);
    }

    public int getId() { return id; }

    /** 0 means "not saved yet". */
    public void setId(int id) {
        if (id < 0) throw new IllegalArgumentException("Household id cannot be negative.");
        this.id = id;
    }

    public String getName() { return name; }

    public void setName(String name) {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Household name is required.");
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH)
            throw new IllegalArgumentException("Household name must be at most 60 characters.");
        this.name = trimmed;
    }

    public String getInviteCode() { return inviteCode; }

    public void setInviteCode(String inviteCode) {
        if (!isValidInviteCode(inviteCode))
            throw new IllegalArgumentException("Invite code must be 6 characters from " + INVITE_ALPHABET + ".");
        this.inviteCode = inviteCode;
    }

    public int getCreatedBy() { return createdBy; }

    /** The creator must be a saved user, so the id must be positive. */
    public void setCreatedBy(int createdBy) {
        if (createdBy <= 0) throw new IllegalArgumentException("Creator user id must be positive.");
        this.createdBy = createdBy;
    }

    public boolean isHideShameRanks() { return hideShameRanks; }

    public void setHideShameRanks(boolean hideShameRanks) { this.hideShameRanks = hideShameRanks; }

    /** True if code is exactly 6 characters, all from the allowed alphabet. */
    public static boolean isValidInviteCode(String code) {
        if (code == null || code.length() != INVITE_CODE_LENGTH) return false;
        for (int i = 0; i < code.length(); i++) {
            if (INVITE_ALPHABET.indexOf(code.charAt(i)) < 0) return false;
        }
        return true;
    }
}