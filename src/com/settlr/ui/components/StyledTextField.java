package com.settlr.ui.components;

import com.settlr.ui.theme.Theme;

import javax.swing.JTextField;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;

/**
 * Rounded text field with gray placeholder text ("Username").
 * The outline turns teal while you type in it.
 *
 * The static helpers are shared with StyledPasswordField. That works because
 * JPasswordField is itself a subclass of JTextField (inheritance).
 */
public class StyledTextField extends JTextField {

    private final String placeholder;

    public StyledTextField(String placeholder) {
        this.placeholder = placeholder;
        applyStyle(this);
    }

    @Override
    protected void paintComponent(Graphics g) {
        paintRoundedBackground(this, g); // 1. white rounded box
        super.paintComponent(g);         // 2. the typed text + cursor
        paintPlaceholder(this, g, placeholder); // 3. gray hint if empty
    }

    // ---------- shared helpers (also used by StyledPasswordField) ----------

    static void applyStyle(JTextField field) {
        RoundedBorder border = new RoundedBorder(Theme.INPUT_BORDER, 2, 24, new Insets(8, 16, 8, 16));
        field.setBorder(border);
        field.setOpaque(false); // we paint our own rounded background
        field.setBackground(Theme.SURFACE);
        field.setForeground(Theme.TEXT);
        field.setCaretColor(Theme.TEXT);
        field.setFont(Theme.font(Font.PLAIN, 14));
        field.setPreferredSize(new Dimension(300, 44));

        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                border.setColor(Theme.PRIMARY);
                field.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                border.setColor(Theme.INPUT_BORDER);
                field.repaint();
            }
        });
    }

    static void paintRoundedBackground(JTextField field, Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(field.getBackground());
        int arc = ((RoundedBorder) field.getBorder()).getArc();
        g2.fill(new RoundRectangle2D.Float(1, 1, field.getWidth() - 2, field.getHeight() - 2, arc, arc));
        g2.dispose();
    }

    static void paintPlaceholder(JTextField field, Graphics g, String placeholder) {
        if (placeholder == null || field.getDocument().getLength() > 0) {
            return; // something is typed, so no hint
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setColor(Theme.MUTED_TEXT);
        g2.setFont(field.getFont());
        FontMetrics fm = g2.getFontMetrics();
        int x = field.getInsets().left;
        int y = (field.getHeight() - fm.getHeight()) / 2 + fm.getAscent(); // vertically centered
        g2.drawString(placeholder, x, y);
        g2.dispose();
    }
}
