package com.settlr.ui.screens;

import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.model.Household;
import com.settlr.service.AuthService;
import com.settlr.service.HouseholdService;
import com.settlr.ui.BaseScreen;
import com.settlr.ui.Navigator;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.StyledTextField;
import com.settlr.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Household Setup screen: two side-by-side cards, "Create a household" and
 * "Join a household". Shown after login to anyone who isn't in a household yet.
 * HouseholdService does the saving; on success the user goes to Home.
 */
public class HouseholdSetupScreen extends BaseScreen {

    private final StyledTextField nameField = new StyledTextField("Household name");
    private final StyledTextField codeField = new StyledTextField("6-character invite code");
    private final JLabel createError = errorLabel();
    private final JLabel joinError = errorLabel();
    private final HouseholdService householdService = new HouseholdService();

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
        content.add(Box.createVerticalStrut(20));
        JPanel logOutRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        logOutRow.setOpaque(false);
        logOutRow.add(logOutLink());
        logOutRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(logOutRow);

        add(content, new GridBagConstraints());
    }

    // ---------- Actions ----------

    private void onCreate() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showError(createError, nameField, "Please enter a household name.");
            return;
        }
        if (name.length() > Household.MAX_NAME_LENGTH) {
            showError(createError, nameField, "Name is too long (max 60 characters).");
            return;
        }
        createError.setText(" ");
        try {
            Household household = householdService.create(name); // you become the Admin
            showInviteCode(household);
            Navigator.getInstance().show(Navigator.DASHBOARD);
        } catch (DataAccessException e) {
            showError(createError, nameField, "Can't reach the database. Is MySQL running?");
            e.printStackTrace();
        } catch (SettlrException e) {
            showError(createError, nameField, e.getMessage());
        }
    }

    private void onJoin() {
        String code = codeField.getText().trim().toUpperCase(); // "abc234" works too
        if (code.length() != Household.INVITE_CODE_LENGTH) {
            showError(joinError, codeField, "Invite codes are exactly 6 characters.");
            return;
        }
        if (!Household.isValidInviteCode(code)) {
            showError(joinError, codeField, "Use letters A-Z (no I or O) and digits 2-9.");
            return;
        }
        joinError.setText(" ");
        try {
            householdService.join(code);
            Navigator.getInstance().show(Navigator.DASHBOARD);
        } catch (DataAccessException e) {
            showError(joinError, codeField, "Can't reach the database. Is MySQL running?");
            e.printStackTrace();
        } catch (SettlrException e) {
            showError(joinError, codeField, e.getMessage()); // e.g. "That invite code doesn't match any household."
        }
    }

    /**
     * The new Admin needs the code to invite roommates. The Household settings
     * screen will show it later; until then this is the only place it appears.
     * The code is in a read-only text field so it can be selected and copied.
     */
    private void showInviteCode(Household household) {
        JTextField code = new JTextField(household.getInviteCode());
        code.setEditable(false);
        code.setFont(Theme.font(Font.BOLD, 22));
        code.setHorizontalAlignment(JTextField.CENTER);
        Object[] message = {
                household.getName() + " is ready! Send this invite code to your roommates:",
                code
        };
        JOptionPane.showMessageDialog(this, message, "Household created", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Without the sidebar there is no other way out of this screen. */
    private JLabel logOutLink() {
        JLabel link = new JLabel("<html><u>Not you? Log out</u></html>");
        link.setFont(Theme.font(Font.PLAIN, 13));
        link.setForeground(Theme.MUTED_TEXT);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                new AuthService().logout();
                Navigator.getInstance().show(Navigator.LOGIN);
            }
        });
        return link;
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
        // Start clean, e.g. after someone else logged out on this computer
        nameField.setText("");
        codeField.setText("");
        createError.setText(" ");
        joinError.setText(" ");
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