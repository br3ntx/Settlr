package com.settlr.ui.screens;

import com.settlr.ui.BaseScreen;
import com.settlr.ui.theme.Theme;

import javax.swing.JLabel;
import java.awt.Font;
import java.awt.GridBagLayout;

/** Stand-in for screens that aren't built yet. Remove each use once the real screen exists. */
public class PlaceholderScreen extends BaseScreen {

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
