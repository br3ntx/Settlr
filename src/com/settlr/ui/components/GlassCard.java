package com.settlr.ui.components;

import com.settlr.ui.theme.Theme;

import javax.swing.JPanel;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * The rounded "glass" card from Figma: a flat body, a soft dark rim along the
 * top and left (inner shadow), a faint light rim along the bottom and right
 * (inner glow), and a thin light edge. Add components to it like any JPanel.
 */
public class GlassCard extends JPanel {

    private static final int RADIUS = 26; // corner roundness from Figma
    private static final int RIM = 18;    // how far the shadow and glow fade in, in px

    public GlassCard(LayoutManager layout) {
        super(layout);
        setOpaque(false); // we paint our own rounded background in paintComponent
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        Shape card = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, RADIUS * 2, RADIUS * 2);

        // 1. Flat body
        g2.setColor(Theme.GLASS_FILL);
        g2.fill(card);

        // 2. Inner shadow (top + left) and inner glow (bottom + right), kept inside the card
        Graphics2D rim = (Graphics2D) g2.create();
        rim.clip(card);
        Color clear = new Color(0, 0, 0, 0);
        rim.setPaint(new GradientPaint(0, 0, Theme.GLASS_SHADOW, 0, RIM, clear));
        rim.fillRect(0, 0, w, RIM);
        rim.setPaint(new GradientPaint(0, 0, Theme.GLASS_SHADOW, RIM, 0, clear));
        rim.fillRect(0, 0, RIM, h);
        rim.setPaint(new GradientPaint(0, h, Theme.GLASS_GLOW, 0, h - RIM, clear));
        rim.fillRect(0, h - RIM, w, RIM);
        rim.setPaint(new GradientPaint(w, 0, Theme.GLASS_GLOW, w - RIM, 0, clear));
        rim.fillRect(w - RIM, 0, RIM, h);
        rim.dispose();

        // 3. Thin light edge
        g2.setColor(Theme.GLASS_EDGE);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(card);
        g2.dispose();
    }
}
