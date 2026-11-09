package com.settlr.util;

import com.settlr.exception.AuthException;
import com.settlr.exception.PermissionDeniedException;
import com.settlr.model.Household;
import com.settlr.model.Role;
import com.settlr.model.User;

/**
 * Remembers who is logged in while the app is running.
 *
 * Singleton: Session.getInstance() always returns the same object, so every
 * screen and service sees the same logged-in user without passing it around.
 * The fields are private (encapsulation); other classes can read them but can
 * only change them through start(), setHousehold() and end().
 *
 * The require...() methods are the "are you logged in / in a household / the Admin?" checks
 * every service starts with, so the rule and the message are the same everywhere.
 */
public final class Session {

    private static Session instance;

    private User currentUser;
    private Household currentHousehold;
    private Role role;

    private Session() { }

    public static synchronized Session getInstance() {
        if (instance == null) {
            instance = new Session();
        }
        return instance;
    }

    /** Call after a successful login, before the user has picked a household. */
    public void start(User user) {
        start(user, null, null);
    }

    /** Call after login when the user already belongs to a household. */
    public void start(User user, Household household, Role role) {
        if (user == null) {
            throw new IllegalArgumentException("Can't start a session without a user.");
        }
        this.currentUser = user;
        this.currentHousehold = household;
        this.role = role;
    }

    /** Call after the user creates or joins a household on the Household Setup screen. */
    public void setHousehold(Household household, Role role) {
        if (currentUser == null) {
            throw new IllegalStateException("Nobody is logged in.");
        }
        this.currentHousehold = household;
        this.role = role;
    }

    /** Call on log out. */
    public void end() {
        currentUser = null;
        currentHousehold = null;
        role = null;
    }

    public User getCurrentUser() { return currentUser; }

    public Household getCurrentHousehold() { return currentHousehold; }

    public Role getRole() { return role; }

    public boolean isLoggedIn() { return currentUser != null; }

    public boolean hasHousehold() { return currentHousehold != null; }

    public boolean isAdmin() { return role == Role.ADMIN; }

    // ---- checks for the services ----------------------------------------

    /** The logged-in user. Fails with "Please log in first." when nobody is. */
    public User requireUser() throws AuthException {
        if (currentUser == null) {
            throw new AuthException("Please log in first.");
        }
        return currentUser;
    }

    /** The current household. Fails when nobody is logged in, or they haven't joined one yet. */
    public Household requireHousehold() throws AuthException, PermissionDeniedException {
        requireUser();
        if (currentHousehold == null) {
            throw new PermissionDeniedException("Please create or join a household first.");
        }
        return currentHousehold;
    }

    /** The current household's id (same checks as requireHousehold()). */
    public int requireHouseholdId() throws AuthException, PermissionDeniedException {
        return requireHousehold().getId();
    }

    /** Fails unless the logged-in user is the Admin of their household. */
    public void requireAdmin() throws AuthException, PermissionDeniedException {
        requireHousehold();
        if (role != Role.ADMIN) {
            throw new PermissionDeniedException("Only the household Admin can do that.");
        }
    }
}
