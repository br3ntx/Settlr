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

    private final JLabel note = new JLabel(" ");

    public PlaceholderScreen(String title) {
        setBackground(Theme.BACKGROUND);
        setLayout(new GridBagLayout());
        JLabel label = new JLabel(title + " \u2014 coming soon");
        label.setFont(Theme.font(Font.BOLD, 22));
        label.setForeground(Theme.MUTED_TEXT);
        add(label, at(0, 0));
    }

    /** A placeholder with one button under the text, so a popup can be tried before its screen exists. */
    public PlaceholderScreen(String title, JButton button) {
        this(title);
        button.setPreferredSize(new Dimension(150, 36));
        add(button, at(1, 18));
        note.setFont(Theme.font(Font.PLAIN, 13));
        note.setForeground(Theme.MINT);
        add(note, at(2, 12));
    }

    /** A short line under the button, e.g. what was just saved. */
    public void setNote(String text) {
        note.setText(text);
    }

    private static GridBagConstraints at(int row, int spaceAbove) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.insets = new Insets(spaceAbove, 0, 0, 0);
        return c;
    }

    @Override
    public void onShow() {
        note.setText(" ");
    }
}
