package com.settlr.ui.components;

import com.settlr.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * The amber-tinted bar for things waiting for you: swaps on Tasks, payments on Expenses.
 * gapAbove is the empty space kept above the bar.
 */
public final class AttentionCard extends JPanel {

    private final int gapAbove;

    public AttentionCard(int gapAbove) {
        this.gapAbove = gapAbove;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(2, 18, 2, 18));
    }

    @Override
    public Insets getInsets() {
        Insets in = super.getInsets();
        return new Insets(in.top + gapAbove, in.left, in.bottom, in.right);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Shape card = new RoundRectangle2D.Float(0.5f, gapAbove + 0.5f, getWidth() - 1f, getHeight() - gapAbove - 1f, 22, 22);
        g2.setColor(Theme.BANNER);
        g2.fill(card);
        g2.setColor(new Color(201, 164, 110, 120));
        g2.draw(card);
        g2.dispose();
    }
}
