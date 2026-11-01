package com.settlr.ui.components;

import com.settlr.ui.theme.Theme;

import javax.swing.JComponent;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** Small colored pill for a status, e.g. "pending" (amber), "Settled up" (mint), "even" (grey),
 *  "Dani \u2022 Unpaid" (light grey). */
public class StatusBadge extends JComponent {

    /** Which color the badge uses. */
    public enum Kind {
        PENDING(Theme.AMBER, new Color(201, 164, 110, 45)),
        OK(Theme.MINT, new Color(95, 208, 176, 38)),
        NEUTRAL(Theme.MUTED_TEXT, new Color(255, 255, 255, 18)),
        LIGHT(new Color(0xC9D0CC), new Color(255, 255, 255, 22));

        final Color text;
        final Color fill;

        Kind(Color text, Color fill) {
            this.text = text;
            this.fill = fill;
        }
    }

    private final String text;
    private final Kind kind;

    public StatusBadge(String text, Kind kind) {
        this.text = text;
        this.kind = kind;
        setFont(Theme.font(Font.BOLD, 11));
        Dimension d = new Dimension(getFontMetrics(getFont()).stringWidth(text) + 16, 20);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int h = 20, y = (getHeight() - h) / 2;
        g2.setColor(kind.fill);
        g2.fill(new RoundRectangle2D.Float(0, y, getWidth(), h, h, h));
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(kind.text);
        g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, y + (h - fm.getHeight()) / 2 + fm.getAscent());
        g2.dispose();
    }
}
