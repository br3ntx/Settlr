package com.settlr.ui.screens;

import com.settlr.model.Cosmetic;
import com.settlr.model.OwnedPet;
import com.settlr.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The pet standing in the open (no box): a soft glow behind it, the pet, and a shadow under it.
 * There are no pet pictures yet, so the pet is a dashed circle, and what it wears shows as small
 * labels around it (hat on top, glasses on the left, neck item lower right). With no pet, it
 * shows a gold "?" instead.
 */
class PetFigure extends JComponent {

    private static final Color LABEL_TEXT = new Color(0xC9D0CC);

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
        float d = Math.min(w, h) * 0.6f;          // the pet's circle
        float cx = w / 2f, cy = h * 0.47f;

        // glow behind
        float glow = Math.min(w, h) * 0.98f;
        g2.setPaint(new RadialGradientPaint(cx, cy, glow / 2,
                new float[] {0f, 1f}, new Color[] {new Color(232, 217, 205, 26), new Color(232, 217, 205, 0)}));
        g2.fill(new Ellipse2D.Float(cx - glow / 2, cy - glow / 2, glow, glow));

        // shadow under
        float sw = d * 0.85f, sh = d * 0.11f, sy = cy + d / 2 + d * 0.1f;
        g2.setPaint(new RadialGradientPaint(cx, sy + sh / 2, sw / 2,
                new float[] {0f, 1f}, new Color[] {new Color(0, 0, 0, 80), new Color(0, 0, 0, 0)}));
        g2.fill(new Ellipse2D.Float(cx - sw / 2, sy, sw, sh));

        Shape circle = new Ellipse2D.Float(cx - d / 2, cy - d / 2, d, d);
        boolean mystery = pet == null;
        g2.setColor(mystery ? new Color(201, 164, 110, 22) : new Color(46, 59, 59, 160));
        g2.fill(circle);
        g2.setColor(mystery ? new Color(201, 164, 110, 150) : new Color(232, 217, 205, 95));
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                new float[] {6f, 5f}, 0f));
        g2.draw(circle);

        if (mystery) {
            g2.setFont(Theme.font(Font.BOLD, Math.round(d * 0.34f)));
            g2.setColor(Theme.AMBER);
            centered(g2, "?", cx, cy);
        } else {
            g2.setColor(Theme.MUTED_TEXT);
            g2.setFont(Theme.font(Font.BOLD, Math.max(10, Math.round(d / 13))));
            FontMetrics fm = g2.getFontMetrics();
            centered(g2, "Pet picture", cx, cy - fm.getHeight() / 2f);
            centered(g2, "goes here", cx, cy + fm.getHeight() / 2f);

            g2.setFont(Theme.font(Font.BOLD, 10));
            Cosmetic hat = pet.getItem(Cosmetic.Slot.HAT);
            Cosmetic glasses = pet.getItem(Cosmetic.Slot.GLASSES);
            Cosmetic neck = pet.getItem(Cosmetic.Slot.NECK);
            if (hat != null) {
                chip(g2, hat.getName(), cx, cy - d / 2 - 4, true);
            }
            if (glasses != null) {
                chip(g2, glasses.getName(), cx - d * 0.42f, cy - d * 0.2f, true);
            }
            if (neck != null) {
                chip(g2, neck.getName(), cx + d * 0.42f, cy + d * 0.36f, true);
            }
        }
        g2.dispose();
    }

    private static void centered(Graphics2D g2, String text, float cx, float cy) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, cx - fm.stringWidth(text) / 2f, cy - fm.getHeight() / 2f + fm.getAscent());
    }

    /** A small label centered on (cx, cy), drawn on a solid background so it reads over the circle. */
    private static void chip(Graphics2D g2, String text, float cx, float cy, boolean upper) {
        String t = upper ? text.toUpperCase() : text;
        FontMetrics fm = g2.getFontMetrics();
        float cw = fm.stringWidth(t) + 16, ch = 20;
        float x = cx - cw / 2, y = cy - ch / 2;
        Shape pill = new RoundRectangle2D.Float(x, y, cw, ch, ch, ch);
        g2.setColor(new Color(0x34403F));
        g2.fill(pill);
        g2.setColor(new Color(255, 255, 255, 40));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(pill);
        g2.setColor(LABEL_TEXT);
        g2.drawString(t, x + 8, y + (ch - fm.getHeight()) / 2 + fm.getAscent());
    }
}
