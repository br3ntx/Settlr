package com.settlr.ui;

import com.settlr.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;

/**
 * Parent of every in-app popup (New expense, Settle up). It dims the whole window
 * and shows the child's card in the middle; Esc or close() removes it.
 *
 * It is drawn inside the main window (on its modal layer) instead of being a
 * separate window, so rounded corners and the dimmed background work on every PC,
 * and dropdown lists and calendars still open on top of it.
 *
 * A child builds its card in its constructor, adds it with add(card), then calls showOver(...).
 */
public abstract class ModalPopup extends JPanel {

    private JLayeredPane layers;
    private final ComponentAdapter resizer = new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
            fitToWindow();
        }
    };

    protected ModalPopup() {
        setOpaque(false);               // paintComponent draws the dim layer
        setLayout(new GridBagLayout()); // one child + no constraints = centered
        setFocusCycleRoot(true);        // Tab stays inside the popup
        // Swallow clicks on the dimmed area so nothing behind the popup reacts
        addMouseListener(new MouseAdapter() { });
        addMouseMotionListener(new MouseAdapter() { });

        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                close();
            }
        });
    }

    /** Shows the popup over the window that contains anyComponent. */
    public void showOver(Component anyComponent) {
        JRootPane root = SwingUtilities.getRootPane(anyComponent);
        if (root == null) {
            throw new IllegalStateException("Open the popup from a component that is inside a window.");
        }
        layers = root.getLayeredPane();
        layers.add(this, JLayeredPane.MODAL_LAYER); // above the screens, below dropdown lists
        layers.addComponentListener(resizer);
        fitToWindow();
    }

    /** Removes the popup from the window. */
    public void close() {
        if (layers == null) {
            return;
        }
        layers.removeComponentListener(resizer);
        layers.remove(this);
        layers.revalidate();
        layers.repaint();
        layers = null;
    }

    private void fitToWindow() {
        setBounds(0, 0, layers.getWidth(), layers.getHeight());
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Theme.OVERLAY);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    /** The round X in the top-right corner of a popup card. Draws its own X so every font works. */
    protected JButton createCloseButton() {
        JButton x = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? Theme.TEXT : Theme.MUTED_TEXT);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2, cy = getHeight() / 2, r = 7;
                g2.drawLine(cx - r, cy - r, cx + r, cy + r);
                g2.drawLine(cx - r, cy + r, cx + r, cy - r);
                g2.dispose();
            }
        };
        x.setPreferredSize(new Dimension(32, 32));
        x.setContentAreaFilled(false);
        x.setBorderPainted(false);
        x.setFocusPainted(false);
        x.setRolloverEnabled(true);
        x.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        x.setToolTipText("Close");
        x.addActionListener(e -> close());
        return x;
    }
}
