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

    private static final int ARC = 24;

    public RoundedButton(String text) {
        super(text);
        setContentAreaFilled(false); // turn off Swing's default painting
        setBorderPainted(false);
        setFocusPainted(false);
        setRolloverEnabled(true);    // so getModel().isRollover() works
        setForeground(Color.WHITE);
        setFont(Theme.font(Font.BOLD, 15));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(300, 44));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color fill = Theme.PRIMARY;
        if (getModel().isPressed() || getModel().isRollover()) {
            fill = Theme.PRIMARY_DARK;
        }
        g2.setColor(fill);
        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), ARC, ARC));
        g2.dispose();

        super.paintComponent(g); // draws the label text on top
    }
}
