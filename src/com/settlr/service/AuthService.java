package com.settlr.service;

import com.settlr.dao.HouseholdDao;
import com.settlr.dao.MembershipDao;
import com.settlr.dao.UserDao;
import com.settlr.exception.AuthException;
import com.settlr.exception.SettlrException;
import com.settlr.exception.ValidationException;
import com.settlr.model.Household;
import com.settlr.model.Membership;
import com.settlr.model.User;
import com.settlr.util.PasswordHasher;
import com.settlr.util.Session;

import java.time.LocalDateTime;

/**
 * Accounts: register, log in, log out (master doc 3.1).
 * Screens call this; it talks to the DAOs and fills in the Session.
 */
public class AuthService {

    /** Same message for a wrong username or a wrong password, so nobody can guess which usernames exist. */
    public static final String LOGIN_FAILED = "Incorrect username or password";

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserDao userDao = new UserDao();
    private final HouseholdDao householdDao = new HouseholdDao();
    private final MembershipDao membershipDao = new MembershipDao();

    /**
     * Checks the username and password, then starts the Session.
     * If the user already belongs to a household, the Session gets that household
     * and role too, so the next screen can be the Dashboard instead of Household Setup.
     *
     * @throws AuthException wrong username or password
     * @throws com.settlr.exception.DataAccessException the database can't be reached
     */
    public User login(String username, String password) throws SettlrException {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new AuthException("Please enter your username and password.");
        }
        User user = userDao.findByUsername(username.trim());
        if (user == null || !PasswordHasher.matches(password, user.getPasswordHash())) {
            throw new AuthException(LOGIN_FAILED);
        }

        Membership membership = membershipDao.findActiveMembershipOf(user.getId());
        Household household = membership == null ? null : householdDao.findById(membership.getHouseholdId());
        if (household == null) {
            Session.getInstance().start(user);
        } else {
            Session.getInstance().start(user, household, membership.getRole());
        }
        return user;
    }

    /**
     * Creates a new account (it does NOT log in; the Register screen goes back to Login).
     *
     * @throws ValidationException a rule in master doc 3.1 is broken, or the username is taken
     */
    public User register(String username, String displayName, String password, String confirm)
            throws SettlrException {
        String name = username == null ? "" : username.trim();
        String display = displayName == null ? "" : displayName.trim();

        if (name.length() < 3 || name.length() > 30) {
            throw new ValidationException("Username must be 3 to 30 characters.");
        }
        if (!name.matches("[A-Za-z0-9_]+")) {
            throw new ValidationException("Username can only use letters, numbers and underscore.");
        }
        if (display.isEmpty() || display.length() > 50) {
            throw new ValidationException("Display name must be 1 to 50 characters.");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        if (!password.equals(confirm)) {
            throw new ValidationException("Passwords don't match.");
        }
        if (userDao.findByUsername(name) != null) {
            throw new ValidationException("That username is already taken.");
        }

        User user = new User(0, name, display, PasswordHasher.hash(password), LocalDateTime.now());
        userDao.insert(user); // also sets the new id on user
        return user;
    }

    /** Clears the Session. The caller then shows the Login screen. */
    public void logout() {
        Session.getInstance().end();
    }
}
