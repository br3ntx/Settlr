package com.settlr.ui.screens;

import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.StyledPasswordField;
import com.settlr.ui.components.StyledTextField;
import com.settlr.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;


public class LoginScreen extends JPanel {   // extends BaseScreen

    private final StyledTextField usernameField = new StyledTextField("Username");
    private final StyledPasswordField passwordField = new StyledPasswordField("Password");
    private final JLabel errorLabel = new JLabel(" "); // a space keeps its height when empty

    public LoginScreen() {
        setBackground(Theme.BACKGROUND);
        setLayout(new GridBagLayout()); // one child + no constraints = centered

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        ImageIcon raw = new ImageIcon("resources/images/SETTLR LOGO.png");
        Image scaled = raw.getImage().getScaledInstance(90, 90, Image.SCALE_SMOOTH); // size from Figma
        JLabel logo = new JLabel(new ImageIcon(scaled));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(logo);
        content.add(Box.createVerticalStrut(12));


        JLabel title = new JLabel("WELCOME TO SETTLR");
        title.setFont(Theme.font(Font.BOLD, 40));
        title.setForeground(Theme.TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel card = createLoginCard();
        card.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(title);
        content.add(Box.createVerticalStrut(32));
        content.add(card);
        add(content);

        wireUpActions();
    }

    /** The white box with the form inside. */
    private JPanel createLoginCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Theme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                BorderFactory.createEmptyBorder(32, 40, 28, 40)));  // inner padding

        JLabel heading = new JLabel("Log In");
        heading.setFont(Theme.font(Font.BOLD, 22));
        heading.setForeground(Theme.TEXT);

        errorLabel.setFont(Theme.font(Font.PLAIN, 12));
        errorLabel.setForeground(Theme.DANGER);

        RoundedButton loginButton = new RoundedButton("Log In");
        loginButton.addActionListener(e -> handleLogin());

        // GridBagConstraints: where each item goes in the card
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL; // stretch items to the card's width
        c.anchor = GridBagConstraints.WEST;

        c.gridy = 0; c.insets = new Insets(0, 0, 20, 0);
        card.add(heading, c);

        c.gridy = 1; c.insets = new Insets(0, 0, 20, 0);
        card.add(usernameField, c);

        c.gridy = 2; c.insets = new Insets(0, 0, 6, 0);
        card.add(passwordField, c);

        c.gridy = 3; c.insets = new Insets(0, 4, 14, 0);
        card.add(errorLabel, c);

        c.gridy = 4; c.insets = new Insets(0, 0, 18, 0);
        card.add(loginButton, c);

        c.gridy = 5; c.insets = new Insets(0, 0, 0, 0);
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.CENTER;
        card.add(createSignUpRow(), c);

        return card;
    }

    /** "Don't have an account? Sign up" with "Sign up" as a clickable link. */
    private JPanel createSignUpRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        row.setOpaque(false);

        JLabel question = new JLabel("Don't have an account?");
        question.setFont(Theme.font(Font.PLAIN, 13));
        question.setForeground(Theme.MUTED_TEXT);

        JLabel signUpLink = new JLabel("<html><u>Sign up</u></html>");
        signUpLink.setFont(Theme.font(Font.BOLD, 13));
        signUpLink.setForeground(Theme.PRIMARY);
        signUpLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        signUpLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleSignUp();
            }
        });

        row.add(question);
        row.add(signUpLink);
        return row;
    }

    /** Keyboard shortcuts: Enter in username jumps to password; Enter in password logs in. */
    private void wireUpActions() {
        usernameField.addActionListener(e -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(e -> handleLogin());
    }

    private void handleLogin() {
        String username = usernameField.getText().trim();
        char[] password = passwordField.getPassword(); // char[] instead of String is safer for passwords

        if (username.isEmpty() || password.length == 0) {
            errorLabel.setText("Please enter your username and password.");
            return;
        }
        errorLabel.setText(" ");

        // TODO (when AuthService and Session exist):
        //   User user = authService.login(username, new String(password));
        //   Session.getInstance().start(user);
        //   Navigator.getInstance().show("dashboard");
        // If login fails, catch AuthException and show it in errorLabel instead.
        JOptionPane.showMessageDialog(this,
                "Form works! Real login will connect once AuthService is ready.",
                "Settlr", JOptionPane.INFORMATION_MESSAGE);

        Arrays.fill(password, '\0'); // wipe the password from memory
    }

    private void handleSignUp() {
        // TODO (when RegisterScreen and Navigator exist):
        //   Navigator.getInstance().show("register");
        JOptionPane.showMessageDialog(this,
                "Sign-up screen coming soon.",
                "Settlr", JOptionPane.INFORMATION_MESSAGE);
    }
}
