package com.settlr.ui.components;

import com.settlr.ui.theme.Theme;

import javax.swing.JComponent;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.IntConsumer;

/**
 * Two or more options side by side in a dark pill; the picked one is beige.
 * Used for "Pay all | Choose expenses" and "Mine | Everyone".
 *
 *     new SegmentedControl(new String[] {"Mine", "Everyone"}, i -> showEveryone(i == 1));
 */
public class SegmentedControl extends JComponent {

    private static final int PAD = 16, HEIGHT = 32, INSET = 3;

    private final String[] options;
    private final int[] widths;
    private int selected;

    /** onChange gets the index of the option clicked (only when it changes). */
    public SegmentedControl(String[] options, IntConsumer onChange) {
        this.options = options;
        setFont(Theme.font(Font.BOLD, 12));
        FontMetrics fm = getFontMetrics(getFont());
        widths = new int[options.length];
        int total = INSET * 2;
        for (int i = 0; i < options.length; i++) {
            widths[i] = fm.stringWidth(options[i]) + PAD * 2;
            total += widths[i];
        }
        Dimension d = new Dimension(total, HEIGHT);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int x = INSET;
                for (int i = 0; i < widths.length; i++) {
                    if (e.getX() < x + widths[i] || i == widths.length - 1) {
                        if (i != selected) {
                            setSelected(i);
                            onChange.accept(i);
                        }
                        return;
                    }
                    x += widths[i];
                }
            }
        });
    }

    public int getSelected() {
        return selected;
    }

    /** Picks an option without calling onChange. */
    public void setSelected(int index) {
        selected = index;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g2.setColor(new Color(0x1C2828));
        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        int x = INSET;
        for (int i = 0; i < options.length; i++) {
            if (i == selected) {
                g2.setColor(Theme.PRIMARY);
                g2.fill(new RoundRectangle2D.Float(x, INSET, widths[i], h - INSET * 2, h - INSET * 2, h - INSET * 2));
            }
            g2.setColor(i == selected ? Theme.ON_PRIMARY : Theme.MUTED_TEXT);
            g2.drawString(options[i], x + (widths[i] - fm.stringWidth(options[i])) / 2,
                    (h - fm.getHeight()) / 2 + fm.getAscent());
            x += widths[i];
        }
        g2.dispose();
    }
}
