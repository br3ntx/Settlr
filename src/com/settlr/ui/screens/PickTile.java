package com.settlr.ui.screens;

import com.settlr.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;

/**
 * A pickable tile for the pet popups: a bubble where the item's picture will go, with its
 * name under it. The picked one gets a beige outline and a check. Used for hats and
 * accessories in Edit pet, and for pets in Change pet.
 */
class PickTile extends JPanel {

    /** What goes in the middle of the bubble until there are pictures. */
    enum Look { ITEM, PET, NONE }

    private final Bubble bubble;

    PickTile(String name, Look look, String tag, Color tagColor, int bubbleHeight, Runnable onPick) {
        super(new BorderLayout(0, 6));
        setOpaque(false);
        bubble = new Bubble(look, tag, tagColor);
        bubble.setPreferredSize(new Dimension(60, bubbleHeight));
        add(bubble, BorderLayout.CENTER);
        JLabel label = new JLabel(name, SwingConstants.CENTER);
        label.setFont(Theme.font(Font.BOLD, 12));
        label.setForeground(Theme.TEXT);
        label.setBorder(new EmptyBorder(0, 0, 0, 0));
        add(label, BorderLayout.SOUTH);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { onPick.run(); }
            @Override public void mouseEntered(MouseEvent e) { bubble.hover = true; bubble.repaint(); }
            @Override public void mouseExited(MouseEvent e) { bubble.hover = false; bubble.repaint(); }
        });
    }

    void setPicked(boolean picked) {
        bubble.picked = picked;
        bubble.repaint();
    }

    private static final class Bubble extends JComponent {
        private final Look look;
        private final String tag;
        private final Color tagColor;
        boolean picked;
        boolean hover;

        Bubble(Look look, String tag, Color tagColor) {
            this.look = look;
            this.tag = tag;
            this.tagColor = tagColor;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int pad = 8; // room for the check that pokes out of the corner
            float w = getWidth() - pad * 2f, h = getHeight() - pad * 2f;
            Shape box = new RoundRectangle2D.Float(pad, pad, w, h, 22, 22);
            g2.setColor(picked ? new Color(232, 217, 205, 26) : new Color(0x2E3B3B));
            g2.fill(box);
            g2.setColor(picked ? Theme.PRIMARY : hover ? new Color(232, 217, 205, 120) : new Color(255, 255, 255, 50));
            g2.setStroke(new BasicStroke(picked ? 2f : 1f));
            g2.draw(box);

            float cx = pad + w / 2, cy = pad + h / 2 + (tag == null ? 0 : 6);
            float s = Math.min(w, h) * 0.42f;
            Color faint = new Color(232, 217, 205, 80);
            if (look == Look.NONE) {
                g2.setColor(faint);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new Ellipse2D.Float(cx - s / 2, cy - s / 2, s, s));
                float r = s / 2 * 0.72f;
                g2.draw(new Line2D.Float(cx - r, cy + r, cx + r, cy - r));
            } else {
                g2.setColor(faint);
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                        new float[] {4f, 4f}, 0f));
                Shape ph = look == Look.PET ? new Ellipse2D.Float(cx - s / 2, cy - s / 2, s, s)
                        : new RoundRectangle2D.Float(cx - s / 2, cy - s / 2, s, s, 10, 10);
                g2.draw(ph);
            }

            if (tag != null) {
                g2.setFont(Theme.font(Font.BOLD, 10));
                FontMetrics fm = g2.getFontMetrics();
                int cw = fm.stringWidth(tag) + 14, ch = 18;
                int tx = pad + 9, ty = pad + 8;
                g2.setColor(new Color(tagColor.getRed(), tagColor.getGreen(), tagColor.getBlue(), 38));
                g2.fill(new RoundRectangle2D.Float(tx, ty, cw, ch, ch, ch));
                g2.setColor(tagColor);
                g2.drawString(tag, tx + 7, ty + (ch - fm.getHeight()) / 2 + fm.getAscent());
            }

            if (picked) {
                int d = 20;
                float x = pad + w - d / 2f - 2, y = pad - d / 2f + 2;
                g2.setColor(Theme.PRIMARY);
                g2.fill(new Ellipse2D.Float(x, y, d, d));
                g2.setColor(Theme.ON_PRIMARY);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(new int[] {(int) x + 5, (int) x + 9, (int) x + 15},
                        new int[] {(int) y + 10, (int) y + 14, (int) y + 7}, 3);
            }
            g2.dispose();
        }
    }
}
