package com.settlr.ui.components;

import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * A rounded outline with padding inside it. Used by the text fields.
 * The color can change (e.g. gray normally, teal when the field is focused).
 */
public class RoundedBorder extends AbstractBorder {

    private Color color;
    private final int thickness;
    private int arc;              // how round the corners are (bigger than the height = pill)
    private final Insets padding; // space between the outline and the text

    public RoundedBorder(Color color, int thickness, int arc, Insets padding) {
        this.color = color;
        this.thickness = thickness;
        this.arc = arc;
        this.padding = padding;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public int getArc() {
        return arc;
    }

    public void setArc(int arc) {
        this.arc = arc;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(thickness));
        float half = thickness / 2f; // keeps the stroke fully inside the component
        int a = Math.min(arc, height); // never rounder than the height, so big values make a pill, not an oval
        g2.draw(new RoundRectangle2D.Float(x + half, y + half,
                width - thickness, height - thickness, a, a));
        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(padding.top, padding.left, padding.bottom, padding.right);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.set(padding.top, padding.left, padding.bottom, padding.right);
        return insets;
    }
}
