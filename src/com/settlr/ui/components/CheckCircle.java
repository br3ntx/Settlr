package com.settlr.ui.components;

import com.settlr.model.TaskStatus;
import com.settlr.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;

/** The round tick button. Mint when done, amber when done late, red ring when overdue or missed. */
public final class CheckCircle extends JComponent {
    private static final int SIZE = 22;
    private final TaskStatus status;
    private final boolean overdue;
    private final boolean clickable;
    private boolean hover;

    public CheckCircle(TaskStatus status, boolean overdue, boolean clickable, Runnable onTick) {
        this.status = status;
        this.overdue = overdue;
        this.clickable = clickable;
        Dimension d = new Dimension(SIZE + 2, SIZE + 2);
        setPreferredSize(d);
        setMinimumSize(d);
        if (clickable) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Mark as done");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { onTick.run(); }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float x = 1, y = (getHeight() - SIZE) / 2f;
        Shape circle = new Ellipse2D.Float(x, y, SIZE, SIZE);
        boolean done = status == TaskStatus.DONE || status == TaskStatus.DONE_LATE;
        if (done) {
            g2.setColor(status == TaskStatus.DONE ? Theme.MINT : Theme.AMBER);
            g2.fill(circle);
            drawTick(g2, x, y, Theme.ON_MINT);
        } else {
            Color ring = status == TaskStatus.MISSED || overdue ? Theme.DANGER
                    : clickable ? new Color(232, 217, 205, 150) : new Color(232, 217, 205, 60);
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(ring);
            g2.draw(new Ellipse2D.Float(x + 1, y + 1, SIZE - 2, SIZE - 2));
            if (hover) {
                g2.setColor(new Color(95, 208, 176, 60));
                g2.fill(circle);
                drawTick(g2, x, y, Theme.MINT);
            }
        }
        g2.dispose();
    }

    private static void drawTick(Graphics2D g2, float x, float y, Color color) {
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawPolyline(new int[] {(int) x + 6, (int) x + 10, (int) x + 16},
                new int[] {(int) y + 11, (int) y + 15, (int) y + 8}, 3);
    }
}
