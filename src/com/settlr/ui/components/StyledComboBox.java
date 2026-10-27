package com.settlr.ui.components;

import com.settlr.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.Function;

/**
 * Pill-shaped dropdown with a beige outline and a chevron (Figma "Category", "Paid by").
 *
 * @param <T> what the list holds, e.g. ExpenseCategory or User
 */
public class StyledComboBox<T> extends JComboBox<T> {

    private final Function<T, String> label;

    /**
     * @param items what to choose from
     * @param label how to show one item, e.g. User::getDisplayName
     */
    public StyledComboBox(List<T> items, Function<T, String> label) {
        this.label = label;
        for (T item : items) {
            addItem(item);
        }
        setUI(new PillUI());
        setOpaque(false);
        setFont(Theme.font(Font.BOLD, 12));
        setForeground(Theme.TEXT);
        setBackground(Theme.GLASS_FILL);
        setBorder(new RoundedBorder(Theme.PRIMARY_DARK, 2, 1000, new Insets(0, 14, 0, 8)));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setMaximumRowCount(8);
        setRenderer(new Renderer());
    }

    /** The chosen item, already cast to T. */
    @SuppressWarnings("unchecked")
    public T getSelected() {
        return (T) getSelectedItem();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        int h = getHeight();
        g2.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, h - 2, h, h));
        g2.dispose();
        super.paintComponent(g);
    }

    /** How each item looks: in the closed box (index -1) and in the open list. */
    private class Renderer extends DefaultListCellRenderer {
        @Override
        @SuppressWarnings("unchecked")
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, false);
            setText(value == null ? "" : label.apply((T) value));
            setFont(StyledComboBox.this.getFont());
            if (index == -1) { // the text shown inside the closed pill
                setOpaque(false);
                setForeground(Theme.TEXT);
                setBorder(new EmptyBorder(0, 0, 0, 0));
            } else {
                setOpaque(true);
                setBackground(selected ? Theme.PRIMARY : Theme.INPUT_BACKGROUND);
                setForeground(selected ? Theme.ON_PRIMARY : Theme.TEXT);
                setBorder(new EmptyBorder(6, 12, 6, 12));
            }
            return this;
        }
    }

    /** Removes Swing's default box look and draws a chevron instead of the arrow button. */
    private static class PillUI extends BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            JButton b = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Theme.TEXT);
                    g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    g2.drawPolyline(new int[]{cx - 4, cx, cx + 4}, new int[]{cy - 2, cy + 2, cy - 2}, 3);
                    g2.dispose();
                }
            };
            b.setContentAreaFilled(false);
            b.setBorderPainted(false);
            b.setFocusPainted(false);
            b.setOpaque(false);
            b.setBorder(new EmptyBorder(0, 0, 0, 0));
            return b;
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
            // nothing: the pill background is painted by the combo box itself
        }

        @Override
        public void paintCurrentValue(Graphics g, Rectangle bounds, boolean hasFocus) {
            super.paintCurrentValue(g, bounds, false); // no blue focus box
        }

        @Override
        protected ComboPopup createPopup() {
            BasicComboPopup popup = new BasicComboPopup(comboBox);
            popup.setBorder(new LineBorder(Theme.BORDER, 1));
            popup.getList().setBackground(Theme.INPUT_BACKGROUND);
            return popup;
        }
    }
}
