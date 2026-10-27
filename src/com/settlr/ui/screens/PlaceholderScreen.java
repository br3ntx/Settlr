package com.settlr.ui.screens;

import com.settlr.ui.BaseScreen;
import com.settlr.ui.theme.Theme;

import javax.swing.JButton;
import javax.swing.JLabel;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/** Stand-in for screens that aren't built yet. Remove each use once the real screen exists. */
public class PlaceholderScreen extends BaseScreen {

    /** A placeholder with one button under the text, e.g. "+ Add expenses" until the real screen exists. */
    public PlaceholderScreen(String title, JButton button) {
        this(title);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 1;
        c.insets = new Insets(18, 0, 0, 0);
        button.setPreferredSize(new Dimension(170, 38));
        add(button, c);
    }

    public PlaceholderScreen(String title) {
        setBackground(Theme.BACKGROUND);
        setLayout(new GridBagLayout());
        JLabel label = new JLabel(title + " \u2014 coming soon");
        label.setFont(Theme.font(Font.BOLD, 22));
        label.setForeground(Theme.MUTED_TEXT);
        add(label);
    }

    @Override
    public void onShow() {
        // nothing to load yet
    }
}
