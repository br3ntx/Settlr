package com.settlr.util;

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
}
