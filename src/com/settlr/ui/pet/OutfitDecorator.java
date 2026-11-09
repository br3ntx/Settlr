package com.settlr.ui.pet;

import com.settlr.model.Cosmetic;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;

/**
 * A pet wearing one more item (the "decorator" of the Decorator pattern). It draws whatever it
 * wraps first, then its own item on top, so items stack in the order they were wrapped.
 *
 * Each slot has its own subclass that only says WHERE the item goes (HatDecorator: on top,
 * GlassesDecorator: on the face, NeckDecorator: lower down). There are no item pictures yet, so
 * the item shows as a small label in that spot; when pictures exist, only drawItem changes.
 */
public abstract class OutfitDecorator implements PetDrawing {

    private static final Color LABEL_TEXT = new Color(0xC9D0CC);

    private final PetDrawing wearer;
    protected final Cosmetic item;

    protected OutfitDecorator(PetDrawing wearer, Cosmetic item) {
        if (wearer == null || item == null) {
            throw new IllegalArgumentException("A decorator needs a pet and an item.");
        }
        this.wearer = wearer;
        this.item = item;
    }

    @Override
    public final void draw(Graphics2D g2, float cx, float cy, float size) {
        wearer.draw(g2, cx, cy, size); // the pet (and anything it already wears) first
        Point2D.Float spot = where(cx, cy, size * 0.72f);
        drawItem(g2, spot.x, spot.y);
    }

    /** Where this slot's item goes on a pet of size d centered on (cx, cy). */
    protected abstract Point2D.Float where(float cx, float cy, float d);

    /** Draws the item centered on that spot. For now: its name in a small label. */
    protected void drawItem(Graphics2D g2, float x, float y) {
        g2.setFont(com.settlr.ui.theme.Theme.font(Font.BOLD, 10));
        String text = item.getName().toUpperCase();
        FontMetrics fm = g2.getFontMetrics();
        float w = fm.stringWidth(text) + 16, h = 20;
        Shape pill = new RoundRectangle2D.Float(x - w / 2, y - h / 2, w, h, h, h);
        g2.setColor(new Color(0x34403F));
        g2.fill(pill);
        g2.setColor(new Color(255, 255, 255, 40));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(pill);
        g2.setColor(LABEL_TEXT);
        g2.drawString(text, x - w / 2 + 8, y - h / 2 + (h - fm.getHeight()) / 2 + fm.getAscent());
    }
}
