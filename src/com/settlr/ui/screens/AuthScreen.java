package com.settlr.ui.screens;

import com.settlr.ui.BaseScreen;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.StyledPasswordField;
import com.settlr.ui.components.StyledTextField;
import com.settlr.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Parent of LoginScreen and RegisterScreen (inheritance): both show the logo,
 * "WELCOME TO SETTLR" and a glass card with a form, and neither shows the sidebar.
 * This class builds the shared parts; each child only builds its own form.
 */
public abstract class AuthScreen extends BaseScreen {

    /** Width of the fields, button and text inside the card (Figma). */
    protected static final int FORM_WIDTH = 270;
    protected static final int FIELD_HEIGHT = 36;

    /** One line for errors (red) and notices (beige), between the fields and the button. */
    protected final JLabel messageLabel = new JLabel(" "); // a space keeps its height when empty

    private final JPanel content = new JPanel();

    protected AuthScreen() {
        setBackground(Theme.BACKGROUND);
        setLayout(new GridBagLayout()); // one child + no constraints = centered

        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        ImageIcon raw = new ImageIcon("resources/images/SETTLR LOGO.png");
        JLabel logo = new JLabel(new ImageIcon(raw.getImage().getScaledInstance(64, 64, Image.SCALE_SMOOTH)));
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(logo);
        content.add(Box.createVerticalStrut(10));

        JLabel title = new JLabel("WELCOME TO SETTLR");
        title.setFont(Theme.font(Font.BOLD, 32));
        title.setForeground(Theme.TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(24));

        messageLabel.setFont(Theme.font(Font.PLAIN, 12));
        messageLabel.setBorder(new EmptyBorder(0, 6, 0, 0)); // lines up with the text inside the fields
        add(content);
    }

    // ---------- for the child screens ----------

    /** An empty glass card; the child adds its rows with addRow(). */
    protected GlassCard createCard() {
        GlassCard card = new GlassCard(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(30, 60, 26, 60)); // inner padding
        return card;
    }

    /** Puts the finished card under the title. Call once, at the end of the child's constructor. */
    protected void showCard(GlassCard card) {
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(card);
    }

    /** Adds one row to the card, with some empty space above it. */
    protected void addRow(GlassCard card, JComponent row, int spaceAbove, int height) {
        if (spaceAbove > 0) {
            card.add(Box.createVerticalStrut(spaceAbove));
        }
        Dimension size = new Dimension(FORM_WIDTH, height);
        row.setPreferredSize(size);
        row.setMinimumSize(size);
        row.setMaximumSize(size);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(row);
    }

    protected StyledTextField createTextField(String placeholder) {
        StyledTextField field = new StyledTextField(placeholder);
        StyledTextField.usePillStyle(field);
        return field;
    }

    protected StyledPasswordField createPasswordField(String placeholder) {
        StyledPasswordField field = new StyledPasswordField(placeholder);
        StyledTextField.usePillStyle(field);
        return field;
    }

    /** Small beige label above a field ("Username"). */
    protected JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.font(Font.BOLD, 13));
        label.setForeground(Theme.PRIMARY);
        label.setBorder(new EmptyBorder(0, 6, 0, 0)); // lines up with the text inside the field
        return label;
    }

    protected RoundedButton createButton(String text, Runnable action) {
        RoundedButton button = new RoundedButton(text);
        button.setArc(RoundedButton.PILL);
        button.setFont(Theme.font(Font.BOLD, 13));
        button.addActionListener(e -> action.run());
        return button;
    }

    /** "Don't have an account? Sign up here" with the second part clickable. */
    protected JPanel createLinkRow(String question, String linkText, Runnable action) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        row.setOpaque(false);

        JLabel q = new JLabel(question);
        q.setFont(Theme.font(Font.PLAIN, 12));
        q.setForeground(Theme.MUTED_TEXT);

        JLabel link = new JLabel(linkText);
        link.setFont(Theme.font(Font.BOLD, 12));
        link.setForeground(Theme.PRIMARY);
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                link.setText("<html><u>" + linkText + "</u></html>");
            }

            @Override
            public void mouseExited(MouseEvent e) {
                link.setText(linkText);
            }
        });

        row.add(q);
        row.add(link);
        return row;
    }

    protected void showError(String message) {
        messageLabel.setForeground(Theme.DANGER);
        messageLabel.setText(message);
    }

    protected void showNotice(String message) {
        messageLabel.setForeground(Theme.PRIMARY);
        messageLabel.setText(message);
    }

    protected void clearMessage() {
        messageLabel.setText(" ");
    }

    // ---------- BaseScreen ----------

    @Override
    public boolean showsSidebar() {
        return false;
    }

    @Override
    public boolean requiresLogin() {
        return false; // you can't need to be logged in to log in or sign up
    }
}
