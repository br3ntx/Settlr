package com.settlr.ui.components;

import com.settlr.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** A thin rounded bar filled mint for done/total. */
public final class ProgressBar extends JComponent {
    private final int done, total;

    public ProgressBar(int done, int total, int height) {
        this.done = done;
        this.total = total;
        setPreferredSize(new Dimension(10, height));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int h = getHeight();
        g2.setColor(new Color(255, 255, 255, 25));
        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), h, h, h));
        if (total > 0 && done > 0) {
            g2.setColor(Theme.MINT);
            g2.fill(new RoundRectangle2D.Float(0, 0, Math.max(h, getWidth() * done / (float) total), h, h, h));
        }
        g2.dispose();
    }
}
