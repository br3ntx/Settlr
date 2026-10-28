package com.settlr.ui.screens;

import com.settlr.exception.AuthException;
import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.service.AuthService;
import com.settlr.ui.Navigator;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.StyledPasswordField;
import com.settlr.ui.components.StyledTextField;
import com.settlr.util.Session;

import javax.swing.SwingUtilities;
import java.util.Arrays;

/** Landing page: username, password, Log In, and a link to Sign Up. */
public class LoginScreen extends AuthScreen {

    private final StyledTextField usernameField = createTextField("Username");
    private final StyledPasswordField passwordField = createPasswordField("Password");
    private final AuthService authService = new AuthService();

    public LoginScreen() {
        GlassCard card = createCard();
        addRow(card, usernameField, 0, FIELD_HEIGHT);
        addRow(card, passwordField, 16, FIELD_HEIGHT);
        addRow(card, messageLabel, 4, 18);
        addRow(card, createButton("Log In", this::handleLogin), 6, FIELD_HEIGHT);
        addRow(card, createLinkRow("Don't have an account?", "Sign up here",
                () -> Navigator.getInstance().show(Navigator.REGISTER)), 12, 18);
        showCard(card);

        // Enter in username jumps to password; Enter in password logs in
        usernameField.addActionListener(e -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(e -> handleLogin());
    }

    private void handleLogin() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword(); // char[] instead of String is safer for passwords

        if (username.isEmpty() || password.length == 0) {
            showError("Please enter your username and password.");
            return;
        }
        clearMessage();

        try {
            authService.login(username, new String(password)); // also starts the Session
            passwordField.setText("");

            if (Session.getInstance().hasHousehold()) {
                Navigator.getInstance().show(Navigator.DASHBOARD);
                // TODO (#3.8, M7): show ReminderDialog here if
                //   new NotificationService().getLoginReminders() is not empty
            } else {
                Navigator.getInstance().show(Navigator.HOUSEHOLD_SETUP);
            }
        } catch (AuthException e) {
            showError(e.getMessage()); // "Incorrect username or password"
            passwordField.setText("");
            passwordField.requestFocusInWindow();
        } catch (DataAccessException e) {
            showError("Can't reach the database. Is MySQL running?");
            e.printStackTrace(); // the real cause, for whoever is debugging
        } catch (SettlrException e) {
            showError(e.getMessage());
        } catch (RuntimeException e) {
            // A bug, not a wrong password: show something instead of failing silently
            showError("Login failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            Arrays.fill(password, '\0'); // wipe the password from memory
        }
    }

    /** Called by RegisterScreen after a new account is made: fill in the username, ask for the password. */
    public void showAccountCreated(String username) {
        usernameField.setText(username);
        showNotice("Account created! Log in to continue.");
        SwingUtilities.invokeLater(passwordField::requestFocusInWindow);
    }

    @Override
    public void onShow() {
        // Shown at startup and after log out: start with an empty form
        usernameField.setText("");
        passwordField.setText("");
        passwordField.setPasswordVisible(false);
        clearMessage();
        SwingUtilities.invokeLater(usernameField::requestFocusInWindow);
    }
}
