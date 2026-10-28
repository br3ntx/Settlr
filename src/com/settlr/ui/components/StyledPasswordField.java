package com.settlr.ui.components;

import com.settlr.ui.theme.Theme;

import javax.swing.JPasswordField;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * Same look as StyledTextField, but hides what you type, with an eye icon on
 * the right: click it to show the password, click again to hide it.
 * Reuses StyledTextField's helpers because JPasswordField extends JTextField.
 */
public class StyledPasswordField extends JPasswordField {

    private static final int EYE_AREA = 40; // clickable width at the right end, in px

    private final String placeholder;
    private final char hiddenEchoChar;      // the dot this look-and-feel uses for hidden text
    private boolean visible;
    private boolean overEye;

    public StyledPasswordField(String placeholder) {
        this.placeholder = placeholder;
        this.hiddenEchoChar = getEchoChar();
        StyledTextField.applyStyle(this);

        // Leave room on the right so the text never runs under the eye
        RoundedBorder border = (RoundedBorder) getBorder();
        Insets in = border.getBorderInsets(this);
        border.setPadding(new Insets(in.top, in.left, in.bottom, EYE_AREA));

        MouseAdapter eyeMouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (isOnEye(e)) {
                    setPasswordVisible(!visible);
                    e.consume();
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                boolean now = isOnEye(e);
                if (now != overEye) {
                    overEye = now;
                    setCursor(Cursor.getPredefinedCursor(now ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR));
                    setToolTipText(now ? (visible ? "Hide password" : "Show password") : null);
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                overEye = false;
                repaint();
            }
        };
        addMouseListener(eyeMouse);
        addMouseMotionListener(eyeMouse);
    }

    /** Shows the typed characters (true) or dots (false). Screens call setPasswordVisible(false) when they reset. */
    public void setPasswordVisible(boolean show) {
        visible = show;
        setEchoChar(show ? (char) 0 : hiddenEchoChar); // (char) 0 means "show the real characters"
        setToolTipText(overEye ? (show ? "Hide password" : "Show password") : null);
        repaint();
    }

    public boolean isPasswordVisible() {
        return visible;
    }

    private boolean isOnEye(MouseEvent e) {
        return e.getX() >= getWidth() - EYE_AREA;
    }

    @Override
    protected void paintComponent(Graphics g) {
        StyledTextField.paintRoundedBackground(this, g);
        super.paintComponent(g);
        StyledTextField.paintPlaceholder(this, g, placeholder);
        paintEye(g);
    }

    /** An eye; with a line through it while the password is visible (click = hide). */
    private void paintEye(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(overEye ? Theme.PRIMARY : Theme.MUTED_TEXT);
        g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        float cx = getWidth() - EYE_AREA / 2f - 2;
        float cy = getHeight() / 2f;
        float w = 9f, h = 5.5f; // half width and half height of the eye

        Path2D eye = new Path2D.Float();
        eye.moveTo(cx - w, cy);
        eye.quadTo(cx, cy - h * 2, cx + w, cy);
        eye.quadTo(cx, cy + h * 2, cx - w, cy);
        eye.closePath();
        g2.draw(eye);
        g2.draw(new Ellipse2D.Float(cx - 2.6f, cy - 2.6f, 5.2f, 5.2f));

        if (visible) {
            g2.drawLine(Math.round(cx - w + 1), Math.round(cy + h + 1),
                        Math.round(cx + w - 1), Math.round(cy - h - 1));
        }
        g2.dispose();
    }
}
