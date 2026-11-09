package com.settlr.ui.components;

import com.settlr.ui.theme.Theme;

import javax.swing.*;
import java.awt.*;

/**
 * Small helpers every screen uses to build its layout, so a label or a column looks the same
 * everywhere and restyling happens in one place. Screens use them with
 *
 *     import static com.settlr.ui.components.Ui.*;
 *
 *     page.add(left(label("Shop", Font.BOLD, 30, Theme.PRIMARY)));
 */
public final class Ui {

    private Ui() { }

    /** A label in the app font, e.g. label("Rent", Font.BOLD, 14, Theme.TEXT). */
    public static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        return l;
    }

    /** A see-through panel that stacks its children top to bottom. */
    public static JPanel vertical() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    /** Wraps a component so it sits in the middle of whatever space it's given. */
    public static JPanel centered(JComponent c) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.add(c);
        return p;
    }

    /** Lines a component up on the left inside a vertical() panel. */
    public static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    /** Lines a component up on the right inside a vertical() panel. */
    public static <T extends JComponent> T right(T c) {
        c.setAlignmentX(Component.RIGHT_ALIGNMENT);
        return c;
    }

    /** Stops a vertical() panel from stretching the component taller than it needs. */
    public static <T extends JComponent> T fitHeight(T c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }
}
