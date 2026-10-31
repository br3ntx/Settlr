package com.settlr.ui.components;

import com.settlr.ui.theme.Theme;

import javax.swing.JButton;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Teal rounded button that darkens when you hover over or press it.
 * Swing's normal button can't be rounded, so this one paints itself.
 */
public class RoundedButton extends JButton {

    private int arc = 24; // setArc(PILL) makes the ends fully round
    private Color fill = Theme.PRIMARY;
    private Color hoverFill = Theme.PRIMARY_DARK;
    private Color outline;                       // null = filled button; set = outlined button

    public RoundedButton(String text) {
        super(text);
        setContentAreaFilled(false); // turn off Swing's default painting
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);    // so getModel().isRollover() works
        setForeground(Theme.ON_PRIMARY);
        setFont(Theme.font(Font.BOLD, 15));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(300, 44));
    }

    /** Any value at least as tall as the button gives fully round ends. */
    public static final int PILL = 1000;

    /** Mint version, like the Figma "Add expenses" button. */
    public static RoundedButton mint(String text) {
        RoundedButton b = new RoundedButton(text);
        b.setColors(Theme.MINT, Theme.MINT_DARK, Theme.ON_MINT);
        return b;
    }

    /** Outlined version with a see-through middle, e.g. the Figma "Cancel" button. */
    public static RoundedButton outline(String text) {
        RoundedButton b = new RoundedButton(text);
        b.outline = new Color(242, 242, 239, 170);
        b.setForeground(Theme.TEXT);
        return b;
    }

    public void setColors(Color fill, Color hoverFill, Color text) {
        this.fill = fill;
        this.hoverFill = hoverFill;
        setForeground(text);
        repaint();
    }

    public void setArc(int arc) {
        this.arc = arc;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        boolean hot = isEnabled() && (getModel().isPressed() || getModel().isRollover());
        int a = Math.min(arc, getHeight()); // PILL never turns into an oval
        if (!isEnabled()) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f)); // faded = can't click
        }
        if (outline != null) {
            if (hot) {
                g2.setColor(new Color(255, 255, 255, 22));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), a, a));
            }
            g2.setColor(outline);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new RoundRectangle2D.Float(0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f, a, a));
        } else {
            g2.setColor(hot ? hoverFill : fill);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), a, a));
        }
        g2.dispose();

        super.paintComponent(g); // draws the label text on top
    }
}
