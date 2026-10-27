package com.settlr.ui.screens;

import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.service.AuthService;
import com.settlr.ui.Navigator;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.StyledPasswordField;
import com.settlr.ui.components.StyledTextField;

import javax.swing.SwingUtilities;
import java.util.Arrays;

/**
 * Sign-up page: username, display name, password twice.
 * AuthService checks the rules (master doc 3.1); on success we go back to Login.
 */
public class RegisterScreen extends AuthScreen {

    private final StyledTextField usernameField = createTextField("3\u201330 letters, digits, _");
    private final StyledTextField displayNameField = createTextField("Your name");
    private final StyledPasswordField passwordField = createPasswordField("At least 6 characters");
    private final StyledPasswordField confirmField = createPasswordField("Confirm password");
    private final AuthService authService = new AuthService();

    public RegisterScreen() {
        GlassCard card = createCard();
        addRow(card, createFieldLabel("Username"), 0, 18);
        addRow(card, usernameField, 4, FIELD_HEIGHT);
        addRow(card, createFieldLabel("Display name"), 12, 18);
        addRow(card, displayNameField, 4, FIELD_HEIGHT);
        addRow(card, createFieldLabel("Password"), 12, 18);
        addRow(card, passwordField, 4, FIELD_HEIGHT);
        addRow(card, createFieldLabel("Confirm password"), 12, 18);
        addRow(card, confirmField, 4, FIELD_HEIGHT);
        addRow(card, messageLabel, 4, 18);
        addRow(card, createButton("Create Account", this::handleCreate), 6, FIELD_HEIGHT);
        addRow(card, createLinkRow("Already have an account?", "Sign in here",
                () -> Navigator.getInstance().show(Navigator.LOGIN)), 12, 18);
        showCard(card);

        // Enter moves to the next field; Enter in the last one creates the account
        usernameField.addActionListener(e -> displayNameField.requestFocusInWindow());
        displayNameField.addActionListener(e -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(e -> confirmField.requestFocusInWindow());
        confirmField.addActionListener(e -> handleCreate());
    }

    private void handleCreate() {
        char[] password = passwordField.getPassword();
        char[] confirm = confirmField.getPassword();
        clearMessage();
        try {
            String username = authService.register(usernameField.getText(), displayNameField.getText(),
                    new String(password), new String(confirm)).getUsername();

            Navigator nav = Navigator.getInstance();
            nav.show(Navigator.LOGIN);
            ((LoginScreen) nav.getScreen(Navigator.LOGIN)).showAccountCreated(username);
        } catch (DataAccessException e) {
            showError("Can't reach the database. Is MySQL running?");
            e.printStackTrace();
        } catch (SettlrException e) {
            showError(e.getMessage()); // e.g. "That username is already taken."
        } catch (RuntimeException e) {
            showError("Sign up failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirm, '\0');
        }
    }

    @Override
    public void onShow() {
        usernameField.setText("");
        displayNameField.setText("");
        passwordField.setText("");
        confirmField.setText("");
        clearMessage();
        SwingUtilities.invokeLater(usernameField::requestFocusInWindow);
    }
}
