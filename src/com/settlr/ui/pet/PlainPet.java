package com.settlr.ui.pet;

import com.settlr.model.Pet;
import com.settlr.ui.components.PetArt;
import com.settlr.ui.theme.Theme;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;

/**
 * The pet on its own, wearing nothing: its picture (PetArt), or a dashed "Pet picture goes here"
 * circle if it has no picture yet. With no pet at all (null) it draws the pet egg.
 * This is the object the outfit decorators wrap.
 */
public class PlainPet implements PetDrawing {

    private final Pet pet; // null = no pet yet: draw the egg

    public PlainPet(Pet pet) {
        this.pet = pet;
    }

    @Override
    public void draw(Graphics2D g2, float cx, float cy, float size) {
        BufferedImage picture = pet == null ? PetArt.egg() : PetArt.of(pet);
        if (picture != null) {
            PetArt.draw(g2, picture, cx - size / 2, cy - size / 2, size, size);
            return;
        }
        drawPlaceholder(g2, cx, cy - size * 0.03f, size * 0.7f);
    }

    /** A dashed circle with a shadow under it: "?" for the egg, "Pet picture goes here" for a pet. */
    private void drawPlaceholder(Graphics2D g2, float cx, float cy, float d) {
        float sw = d * 0.85f, sh = d * 0.11f, sy = cy + d / 2 + d * 0.1f;
        g2.setPaint(new RadialGradientPaint(cx, sy + sh / 2, sw / 2,
                new float[] {0f, 1f}, new Color[] {new Color(0, 0, 0, 80), new Color(0, 0, 0, 0)}));
        g2.fill(new Ellipse2D.Float(cx - sw / 2, sy, sw, sh));

        Shape circle = new Ellipse2D.Float(cx - d / 2, cy - d / 2, d, d);
        boolean egg = pet == null;
        g2.setColor(egg ? new Color(201, 164, 110, 22) : new Color(46, 59, 59, 160));
        g2.fill(circle);
        g2.setColor(egg ? new Color(201, 164, 110, 150) : new Color(232, 217, 205, 95));
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                new float[] {6f, 5f}, 0f));
        g2.draw(circle);

        if (egg) {
            g2.setFont(Theme.font(Font.BOLD, Math.round(d * 0.34f)));
            g2.setColor(Theme.AMBER);
            centered(g2, "?", cx, cy);
        } else {
            g2.setColor(Theme.MUTED_TEXT);
            g2.setFont(Theme.font(Font.BOLD, Math.max(10, Math.round(d / 13))));
            FontMetrics fm = g2.getFontMetrics();
            centered(g2, "Pet picture", cx, cy - fm.getHeight() / 2f);
            centered(g2, "goes here", cx, cy + fm.getHeight() / 2f);
        }
    }

    private static void centered(Graphics2D g2, String text, float cx, float cy) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, cx - fm.stringWidth(text) / 2f, cy - fm.getHeight() / 2f + fm.getAscent());
    }
}
