package com.settlr.ui.pet;

import com.settlr.model.Cosmetic;
import com.settlr.ui.components.CosmeticArt;
import com.settlr.ui.components.PetArt;
import com.settlr.ui.theme.Theme;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * A pet wearing one more item (the "decorator" of the Decorator pattern). It draws whatever it
 * wraps first, then its own item's picture on top, so items stack in the order they were wrapped.
 *
 * One picture per item is enough: the same crown.png goes on the dog and the cat, with or
 * without glasses. (An item may have a separate "on pet" picture; see CosmeticArt.onPet.) Each slot has its own subclass that only works out WHERE the picture goes
 * (HatDecorator: on the head, GlassesDecorator: over the eyes), using the pet's PetFit.
 */
public abstract class OutfitDecorator implements PetDrawing {

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
        drawItem(g2, place(fit(), cx - size / 2, cy - size / 2, size));
    }

    /** The pet underneath decides where its head and eyes are. */
    @Override
    public PetFit fit() {
        return wearer.fit();
    }

    /** The box the item's picture goes in, for a pet drawn in the square (left, top, size). */
    protected abstract Rectangle2D.Float place(PetFit fit, float left, float top, float size);

    /** Height of the item's picture when it's this wide (it keeps its shape). */
    protected float heightFor(float width) {
        BufferedImage img = CosmeticArt.onPet(item);
        return img == null ? width * 0.4f : width * img.getHeight() / img.getWidth();
    }

    /** Draws the item's picture in the box; with no picture, its name in a small label. */
    protected void drawItem(Graphics2D g2, Rectangle2D.Float box) {
        BufferedImage img = CosmeticArt.onPet(item);
        if (img != null) {
            PetArt.draw(g2, img, box.x, box.y, box.width, box.height);
            return;
        }
        g2.setFont(Theme.font(Font.BOLD, 10));
        String text = item.getName().toUpperCase();
        FontMetrics fm = g2.getFontMetrics();
        float w = fm.stringWidth(text) + 16, h = 20;
        float x = (float) box.getCenterX(), y = (float) box.getCenterY();
        Shape pill = new RoundRectangle2D.Float(x - w / 2, y - h / 2, w, h, h, h);
        g2.setColor(new Color(0x34403F));
        g2.fill(pill);
        g2.setColor(new Color(0xC9D0CC));
        g2.drawString(text, x - w / 2 + 8, y - h / 2 + (h - fm.getHeight()) / 2 + fm.getAscent());
    }
}
