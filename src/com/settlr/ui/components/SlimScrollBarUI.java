package com.settlr.ui.components;

import javax.swing.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/** A thin, rounded scrollbar that matches the dark theme instead of Swing's grey one. */
public class SlimScrollBarUI extends BasicScrollBarUI {

    /** Gives a scroll pane slim scrollbars. */
    public static void apply(JScrollPane scroll) {
        scroll.getVerticalScrollBar().setUI(new SlimScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        scroll.getVerticalScrollBar().setOpaque(false);
        scroll.getHorizontalScrollBar().setUI(new SlimScrollBarUI());
        scroll.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 10));
        scroll.getHorizontalScrollBar().setOpaque(false);
    }

    @Override
    protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
        // no track: only the thumb shows
    }

    @Override
    protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
        if (r.isEmpty()) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(255, 255, 255, isThumbRollover() ? 90 : 55));
        g2.fill(new RoundRectangle2D.Float(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6));
        g2.dispose();
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return noButton();
    }

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return noButton();
    }

    private static JButton noButton() {
        JButton b = new JButton();
        b.setPreferredSize(new Dimension(0, 0));
        b.setMinimumSize(new Dimension(0, 0));
        b.setMaximumSize(new Dimension(0, 0));
        return b;
    }
}
