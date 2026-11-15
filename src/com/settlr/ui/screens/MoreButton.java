package com.settlr.ui.screens;

import com.settlr.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;

/**
 * The round "\u00B7\u00B7\u00B7" button that opens a small dark menu, e.g. Make admin /
 * Remove from household on a member, or Pause / Delete on a bill. Each item is added with item(...).
 */
class MoreButton extends JComponent {
    private static final Color MENU_BG = new Color(0x1E2A2A);
    private static final Color MENU_HOVER = new Color(255, 255, 255, 22);
    private final JPopupMenu menu = new JPopupMenu();
    private boolean hover;

    MoreButton() {
        Dimension d = new Dimension(30, 30);
        setPreferredSize(d);
        setMinimumSize(d);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("More");
        menu.setBackground(MENU_BG);
        menu.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 45)), new EmptyBorder(4, 0, 4, 0)));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                menu.show(MoreButton.this, getWidth() - menu.getPreferredSize().width, getHeight() + 4);
            }
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
        });
    }

    /** Adds a line to the menu; danger lines are red. Clicking one closes the menu, then runs the action. */
    void item(String text, boolean danger, Runnable action) {
        MenuItem item = new MenuItem(text, danger);
        item.addActionListener(e -> {
            menu.setVisible(false); // close first, so nothing is left on screen behind a popup
            action.run();
        });
        menu.add(item);
    }

    /** A menu line that draws itself (the look-and-feel's menu items ignore the dark colors). */
    private static final class MenuItem extends JMenuItem {
        private final Color color;

        MenuItem(String text, boolean danger) {
            super(text);
            color = danger ? Theme.DANGER : Theme.TEXT;
            setFont(Theme.font(Font.PLAIN, 13));
            setOpaque(false);
            setBorderPainted(false);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(getFontMetrics(getFont()).stringWidth(getText()) + 36, 32);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(MENU_BG);
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (getModel().isArmed() || getModel().isRollover()) {
                g2.setColor(MENU_HOVER);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            g2.setFont(getFont());
            g2.setColor(color);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(getText(), 16, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (hover || menu.isVisible()) {
            g2.setColor(new Color(255, 255, 255, 25));
            g2.fill(new Ellipse2D.Float(0, 0, getWidth(), getHeight()));
        }
        g2.setColor(new Color(0xC9D0CC));
        int cy = getHeight() / 2;
        for (int i = -1; i <= 1; i++) {
            g2.fill(new Ellipse2D.Float(getWidth() / 2f + i * 6 - 1.6f, cy - 1.6f, 3.2f, 3.2f));
        }
        g2.dispose();
    }
}
