package com.settlr.ui.components;

import com.settlr.ui.theme.Theme;

import javax.swing.JToggleButton;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * A pill you click to include or leave out one member (Figma "Split with").
 * Selected: mint outline and name. Not selected: faded.
 */
public class MemberChip extends JToggleButton {

    private static final int HEIGHT = 34;
    private static final int AVATAR = 22;

    private final int userId;

    public MemberChip(int userId, String name, boolean selected) {
        super(name, selected);
        this.userId = userId;
        setFont(Theme.font(Font.BOLD, 12));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        int textWidth = getFontMetrics(getFont()).stringWidth(name);
        setPreferredSize(new Dimension(textWidth + AVATAR + 34, HEIGHT));
    }

    public int getUserId() {
        return userId;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        boolean on = isSelected();

        // pill
        g2.setColor(on ? new Color(95, 208, 176, 28) : new Color(255, 255, 255, 10));
        g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, h, h));
        g2.setStroke(new BasicStroke(on ? 2f : 1f));
        g2.setColor(on ? Theme.MINT : new Color(255, 255, 255, getModel().isRollover() ? 90 : 45));
        g2.draw(new RoundRectangle2D.Float(1, 1, w - 2, h - 2, h - 2, h - 2));

        // avatar circle with the first letter
        int ax = 6, ay = (h - AVATAR) / 2;
        g2.setColor(Theme.BACKGROUND);
        g2.fill(new Ellipse2D.Float(ax, ay, AVATAR, AVATAR));
        g2.setFont(Theme.font(Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        String initial = getText().isEmpty() ? "?" : getText().substring(0, 1).toUpperCase();
        g2.setColor(on ? Theme.MINT : Theme.MUTED_TEXT);
        g2.drawString(initial, ax + (AVATAR - fm.stringWidth(initial)) / 2,
                ay + (AVATAR - fm.getHeight()) / 2 + fm.getAscent());

        // name
        g2.setFont(getFont());
        fm = g2.getFontMetrics();
        g2.setColor(on ? Theme.MINT : Theme.MUTED_TEXT);
        g2.drawString(getText(), ax + AVATAR + 8, (h - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
