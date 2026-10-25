package com.settlr.ui.screens;

import com.settlr.model.Household;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.StyledTextField;
import com.settlr.ui.BaseScreen;
import com.settlr.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;

/**
 * Household Setup screen: two side-by-side cards, "Create a household" and
 * "Join a household". PREVIEW ONLY: it validates input and shows messages,
 * but saves nothing and talks to no database.
 */
public class HouseholdSetupScreen extends BaseScreen {

    private final StyledTextField nameField = new StyledTextField("Household name");
    private final StyledTextField codeField = new StyledTextField("6-character invite code");
    private final JLabel createError = errorLabel();
    private final JLabel joinError = errorLabel();

    public HouseholdSetupScreen() {
        setBackground(Theme.BACKGROUND);
        setLayout(new GridBagLayout()); // centers the content panel

        JLabel title = new JLabel("Set up your household");
        title.setFont(Theme.font(Font.BOLD, 28));
        title.setForeground(Theme.TEXT);

        JPanel cards = new JPanel(new GridLayout(1, 2, 24, 0));
        cards.setOpaque(false);
        cards.add(buildCard("Create a household", "Start a new place and invite your roommates.",
                nameField, createError, "Create", this::onCreate));
        cards.add(buildCard("Join a household", "Got an invite code? Enter it here.",
                codeField, joinError, "Join", this::onJoin));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(24));
        content.add(cards);

        add(content, new GridBagConstraints());
    }

    // ---------- Actions (validation only, nothing is saved) ----------

    private void onCreate() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showError(createError, nameField, "Please enter a household name.");
        } else if (name.length() > Household.MAX_NAME_LENGTH) {
            showError(createError, nameField, "Name is too long (max 60 characters).");
        } else {
            createError.setText(" ");
            JOptionPane.showMessageDialog(this,
                    "Created! (Preview only: nothing was saved yet.)",
                    "Create a household", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void onJoin() {
        String code = codeField.getText().trim();
        if (code.length() != Household.INVITE_CODE_LENGTH) {
            showError(joinError, codeField, "Invite codes are exactly 6 characters.");
        } else if (!Household.isValidInviteCode(code)) {
            showError(joinError, codeField, "Use capital letters A-Z (no I or O) and digits 2-9.");
        } else {
            joinError.setText(" ");
            JOptionPane.showMessageDialog(this,
                    "Joined! (Preview only: no household was looked up.)",
                    "Join a household", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /** Shows the message, then puts the cursor back in the field with its text selected for quick retyping. */
    private void showError(JLabel label, JTextField field, String message) {
        label.setText(message);
        field.requestFocusInWindow();
        field.selectAll();
    }

    // ---------- Layout helpers ----------

    private JPanel buildCard(String heading, String hint, StyledTextField field,
                             JLabel error, String buttonText, Runnable action) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Theme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                BorderFactory.createEmptyBorder(32, 32, 32, 32)));

        JLabel h = new JLabel(heading);
        h.setFont(Theme.font(Font.BOLD, 22));
        h.setForeground(Theme.TEXT);
        JLabel sub = new JLabel(hint);
        sub.setFont(Theme.font(Font.PLAIN, 13));
        sub.setForeground(Theme.MUTED_TEXT);

        RoundedButton button = new RoundedButton(buttonText);
        button.addActionListener(e -> action.run());
        field.addActionListener(e -> action.run()); // Enter key

        for (JComponent c : new JComponent[]{h, sub, field, error, button}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        // The components are 300 x 44 by default; let them stretch to the card's width.
        for (JComponent c : new JComponent[]{field, button}) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        }

        card.add(h);
        card.add(Box.createVerticalStrut(8));
        card.add(sub);
        card.add(Box.createVerticalStrut(24));
        card.add(field);
        card.add(Box.createVerticalStrut(8));
        card.add(error);
        card.add(Box.createVerticalStrut(12));
        card.add(button);
        return card;
    }

    private static JLabel errorLabel() {
        JLabel l = new JLabel(" "); // blank space keeps the layout from jumping
        l.setFont(Theme.font(Font.PLAIN, 13));
        l.setForeground(Theme.DANGER);
        return l;
    }

    // ---------- BaseScreen ----------

    @Override
    public void onShow() {
        // Nothing to load yet; clear old input here once the form is connected
    }

    @Override
    public boolean showsSidebar() {
        return false;
    }

    // ---------- Temporary preview entry point ----------

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Settlr - Household Setup (preview)");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1100, 700);
            frame.setMinimumSize(new Dimension(800, 600));
            frame.setLocationRelativeTo(null);
            frame.add(new HouseholdSetupScreen());
            frame.setVisible(true);
        });
    }
}