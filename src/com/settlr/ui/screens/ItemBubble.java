package com.settlr.ui.screens;

import com.settlr.model.Cosmetic;
import com.settlr.model.Pet;
import com.settlr.model.ShopItem;
import com.settlr.ui.components.PetArt;
import com.settlr.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * The rounded "bubble" a Shop item sits in, with a small tag in the top-left (HAT, RARE...)
 * and an optional one in the top-right (Owned, NEW). The item's picture goes in the middle
 * later (pets already have theirs, from PetArt). The pet egg uses it too, with a dashed "?" look.
 */
class ItemBubble extends JComponent {

    static final Color XP_GOLD = new Color(0xE6C48A);
    static final Color RARE_BLUE = new Color(0x8DBBEA);
    static final Color EPIC_PURPLE = new Color(0xC39BEF);

    private static final Color TAG_TEXT = new Color(0xC9D0CC);
    private static final Color EDGE = new Color(255, 255, 255, 42);

    private final int arc;
    private String tag;
    private Color tagColor = TAG_TEXT;
    private String cornerTag;
    private Color cornerTagColor;
    private Color outline;  // null = the normal thin edge
    private boolean glow;
    private boolean mystery; // the pet egg
    private BufferedImage picture; // the pet's picture, if it has one
    private boolean hover;

    ItemBubble(int arc) {
        this.arc = arc;
        setOpaque(false);
    }

    /** A bubble for a hat or pet, with its slot or rarity as the tag. */
    static ItemBubble of(ShopItem item, int arc) {
        ItemBubble b = new ItemBubble(arc);
        b.tag = item.getTag();
        if (item instanceof Pet) {
            b.tagColor = rarityColor(((Pet) item).getRarity());
            b.picture = PetArt.of((Pet) item);
        } else if (!(item instanceof Cosmetic)) {
            b.tagColor = TAG_TEXT;
        }
        return b;
    }

    /** The pet egg: its picture on its own, or a dashed gold box with a "?" if the picture is missing. */
    static ItemBubble mystery(int arc) {
        ItemBubble b = new ItemBubble(arc);
        b.mystery = true;
        return b;
    }

    static Color rarityColor(Pet.Rarity rarity) {
        switch (rarity) {
            case RARE: return RARE_BLUE;
            case EPIC: return EPIC_PURPLE;
            default: return TAG_TEXT;
        }
    }

    ItemBubble cornerTag(String text, Color color) {
        cornerTag = text;
        cornerTagColor = color;
        repaint();
        return this;
    }

    ItemBubble outline(Color color, boolean glow) {
        outline = color;
        this.glow = glow;
        repaint();
        return this;
    }

    void setHover(boolean hover) {
        this.hover = hover;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int pad = glow ? 8 : 1; // room for the glow around the edge
        float w = getWidth() - pad * 2f, h = getHeight() - pad * 2f;
        Shape box = new RoundRectangle2D.Float(pad, pad, w, h, arc, arc);

        if (glow && outline != null) {
            for (int i = 6; i >= 1; i--) {
                g2.setColor(new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), 9));
                g2.setStroke(new BasicStroke(i * 2.4f));
                g2.draw(box);
            }
        }

        if (mystery && PetArt.egg() != null) {
            float inset = Math.min(w, h) * 0.06f;
            PetArt.draw(g2, PetArt.egg(), pad + inset, pad + inset, w - 2 * inset, h - 2 * inset);
            g2.dispose();
            return;
        }
        if (mystery) {
            g2.setColor(new Color(201, 164, 110, 28));
            g2.fill(box);
            g2.setColor(new Color(201, 164, 110, 150));
            g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                    new float[] {5f, 4f}, 0f));
            g2.draw(box);
            g2.setFont(Theme.font(Font.BOLD, Math.round(h * 0.36f)));
            g2.setColor(Theme.AMBER);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("?", pad + (w - fm.stringWidth("?")) / 2f, pad + (h - fm.getHeight()) / 2f + fm.getAscent());
            g2.dispose();
            return;
        }

        g2.setPaint(new GradientPaint(0, pad, new Color(0x303D3D), 0, pad + h, new Color(0x283434)));
        g2.fill(box);
        Color edge = outline != null ? outline : hover ? new Color(232, 217, 205, 120) : EDGE;
        g2.setColor(edge);
        g2.setStroke(new BasicStroke(outline != null || hover ? 1.5f : 1f));
        g2.draw(box);

        if (picture != null) { // below the tag, with a little room around it
            PetArt.draw(g2, picture, pad + w * 0.12f, pad + 30, w * 0.76f, h - 30 - 10);
        }

        g2.setFont(Theme.font(Font.BOLD, 11));
        if (tag != null) {
            chip(g2, tag, tagColor, pad + 12, pad + 11, false);
        }
        if (cornerTag != null) {
            chip(g2, cornerTag, cornerTagColor, Math.round(pad + w - 12), pad + 11, true);
        }
        g2.dispose();
    }

    /** A small rounded label; when fromRight, x is where its right edge goes. */
    private static void chip(Graphics2D g2, String text, Color color, int x, int y, boolean fromRight) {
        FontMetrics fm = g2.getFontMetrics();
        int cw = fm.stringWidth(text) + 16, ch = 20;
        int cx = fromRight ? x - cw : x;
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 38));
        g2.fill(new RoundRectangle2D.Float(cx, y, cw, ch, ch, ch));
        g2.setColor(color);
        g2.drawString(text, cx + 8, y + (ch - fm.getHeight()) / 2 + fm.getAscent());
    }
}
