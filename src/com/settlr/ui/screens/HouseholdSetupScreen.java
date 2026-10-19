package com.settlr.ui.screens;

import com.settlr.model.Household;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Household Setup screen: two side-by-side cards, "Create a household" and
 * "Join a household". PREVIEW ONLY: it validates input and shows messages,
 * but saves nothing and talks to no database.
 */
public class HouseholdSetupScreen extends JPanel {

    // Colors and fonts from the master document (section 9), kept local so the file is standalone.
    private static final Color PRIMARY = new Color(0x1F7A68);
    private static final Color PRIMARY_DARK = new Color(0x155A4C);
    private static final Color DANGER = new Color(0xC9443F);
    private static final Color BACKGROUND = new Color(0xF7F5F0);
    private static final Color SURFACE = Color.WHITE;
    private static final Color BORDER = new Color(0xE3DED4);
    private static final Color INPUT_BORDER = new Color(0xBDB6A8);
    private static final Color TEXT = new Color(0x1D2627);
    private static final Color MUTED_TEXT = new Color(0x6B7474);

    private final StyledTextField nameField = new StyledTextField("Household name");
    private final StyledTextField codeField = new StyledTextField("6-character invite code");
    private final JLabel createError = errorLabel();
    private final JLabel joinError = errorLabel();

    public HouseholdSetupScreen() {
        setBackground(BACKGROUND);
        setLayout(new GridBagLayout()); // centers the content panel

        JLabel title = new JLabel("Set up your household");
        title.setFont(font(Font.BOLD, 28));
        title.setForeground(TEXT);

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
            createError.setText("Please enter a household name.");
        } else if (name.length() > Household.MAX_NAME_LENGTH) {
            createError.setText("Name is too long (max 60 characters).");
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
            joinError.setText("Invite codes are exactly 6 characters.");
        } else if (!Household.isValidInviteCode(code)) {
            joinError.setText("Use capital letters A-Z (no I or O) and digits 2-9.");
        } else {
            joinError.setText(" ");
            JOptionPane.showMessageDialog(this,
                    "Joined! (Preview only: no household was looked up.)",
                    "Join a household", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ---------- Layout helpers ----------

    private JPanel buildCard(String heading, String hint, StyledTextField field,
                             JLabel error, String buttonText, Runnable action) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(32, 32, 32, 32)));

        JLabel h = new JLabel(heading);
        h.setFont(font(Font.BOLD, 22));
        h.setForeground(TEXT);
        JLabel sub = new JLabel(hint);
        sub.setFont(font(Font.PLAIN, 13));
        sub.setForeground(MUTED_TEXT);

        RoundedButton button = new RoundedButton(buttonText);
        button.addActionListener(e -> action.run());
        field.addActionListener(e -> action.run()); // Enter key

        for (JComponent c : new JComponent[]{h, sub, field, error, button}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        for (JComponent c : new JComponent[]{field, button}) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            c.setPreferredSize(new Dimension(280, 44));
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
        l.setFont(font(Font.PLAIN, 13));
        l.setForeground(DANGER);
        return l;
    }

    /** Inter if installed, otherwise Segoe UI (same rule as the project's Theme). */
    private static Font font(int style, int size) {
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
            if (f.equals("Inter")) return new Font("Inter", style, size);
        }
        return new Font("Segoe UI", style, size);
    }

    // ---------- Small built-in components (standalone replacements) ----------

    /** Rounded text field with placeholder text and a teal outline when focused. */
    private static class StyledTextField extends JTextField {
        private final String placeholder;
        private boolean focused;

        StyledTextField(String placeholder) {
            this.placeholder = placeholder;
            setOpaque(false);
            setFont(font(Font.PLAIN, 14));
            setForeground(TEXT);
            setCaretColor(TEXT);
            setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 20));
            addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) { focused = true; repaint(); }
                @Override public void focusLost(FocusEvent e) { focused = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(SURFACE);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 24, 24));
            g2.dispose();
            super.paintComponent(g);
            if (getText().isEmpty()) {
                Graphics2D p = (Graphics2D) g.create();
                p.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                p.setColor(MUTED_TEXT);
                p.setFont(getFont());
                p.drawString(placeholder, getInsets().left,
                        (getHeight() + p.getFontMetrics().getAscent() - p.getFontMetrics().getDescent()) / 2);
                p.dispose();
            }
        }

        @Override protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(focused ? PRIMARY : INPUT_BORDER);
            g2.setStroke(new BasicStroke(focused ? 2f : 1f));
            g2.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 3, getHeight() - 3, 24, 24));
            g2.dispose();
        }
    }

    /** Teal rounded button that darkens on hover and press. */
    private static class RoundedButton extends JButton {
        RoundedButton(String text) {
            super(text);
            setFont(font(Font.BOLD, 14));
            setForeground(Color.WHITE);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean dark = getModel().isRollover() || getModel().isPressed();
            g2.setColor(dark ? PRIMARY_DARK : PRIMARY);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));
            g2.dispose();
            super.paintComponent(g);
        }
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