package com.settlr.ui.components;

import com.settlr.ui.theme.Theme;

import javax.swing.JComponent;
import java.awt.*;
import java.awt.geom.Ellipse2D;

/** Round avatar with a person's (or expense's) first letter, until real profile pictures exist. */
public class AvatarLabel extends JComponent {

    private final int size;
    private final String letter;

    public AvatarLabel(String name, int size) {
        this.size = size;
        this.letter = name == null || name.isBlank() ? "?" : name.trim().substring(0, 1).toUpperCase();
        Dimension d = new Dimension(size, size);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int y = (getHeight() - size) / 2;
        g2.setColor(new Color(255, 255, 255, 45));
        g2.fill(new Ellipse2D.Float(0, y, size, size));
        g2.setFont(Theme.font(Font.BOLD, Math.max(11, size * 2 / 5)));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(Theme.TEXT);
        g2.drawString(letter, (size - fm.stringWidth(letter)) / 2, y + (size - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
