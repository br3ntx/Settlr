package com.settlr.ui.screens;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

/**
 * Base for the rows in the Expenses lists: exactly as tall as its content, with an optional
 * divider line under it. ExpenseRow and PaymentRequestRow extend it (inheritance).
 */
abstract class ListRow extends JPanel {

    static final Color DIVIDER = new Color(232, 217, 205, 55);

    private final boolean divider;

    ListRow(boolean divider, int padding) {
        this.divider = divider;
        setOpaque(false);
        setLayout(new BorderLayout(12, 0));
        setBorder(new EmptyBorder(padding, 0, padding, 0));
    }

    /** Never taller than its content, so the list doesn't stretch the rows. */
    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); // grows when tags open
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (divider) {
            g.setColor(DIVIDER);
            g.fillRect(0, getHeight() - 1, getWidth(), 1);
        }
    }

    /** The component on the left, vertically centered. */
    static JPanel leftCentered(JComponent c) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.weightx = 1;
        gc.anchor = GridBagConstraints.WEST;
        p.add(c, gc);
        return p;
    }

    /** The component in the middle, vertically centered. */
    static JPanel centeredVertically(JComponent c) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.add(c);
        return p;
    }
}
