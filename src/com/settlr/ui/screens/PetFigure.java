package com.settlr.ui.screens;

import com.settlr.model.OwnedPet;
import com.settlr.ui.pet.PetDrawings;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

/**
 * The pet standing in the open (no box): a soft glow behind it, then the pet in its outfit.
 * The pet and each item it wears are drawn by a chain of decorators (see com.settlr.ui.pet),
 * so this class only paints the glow and hands the rest to PetDrawings.of(pet).
 * With no pet (null), it shows the pet egg.
 */
class PetFigure extends JComponent {

    private OwnedPet pet; // null = no pet yet

    PetFigure(int width, int height) {
        Dimension d = new Dimension(width, height);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
        setOpaque(false);
    }

    void setPet(OwnedPet pet) {
        this.pet = pet;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h * 0.5f;

        float glow = Math.min(w, h) * 0.98f;
        g2.setPaint(new RadialGradientPaint(cx, h * 0.47f, glow / 2,
                new float[] {0f, 1f}, new Color[] {new Color(232, 217, 205, 26), new Color(232, 217, 205, 0)}));
        g2.fill(new Ellipse2D.Float(cx - glow / 2, h * 0.47f - glow / 2, glow, glow));

        PetDrawings.of(pet).draw(g2, cx, cy, Math.min(w, h) * 0.86f);
        g2.dispose();
    }
}
