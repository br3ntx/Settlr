package com.settlr.ui;

import com.settlr.ui.theme.Theme;

import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Parent class of every screen in Settlr.
 *
 * The Navigator calls onShow() every time a screen appears, so each screen
 * reloads its own data there and is never out of date. Because every screen
 * is a BaseScreen, the Navigator can treat them all the same way without
 * knowing which screen it is (polymorphism).
 */
public abstract class BaseScreen extends JPanel {

    /** The green or red line under a screen's title. Each screen decides where to put it. */
    protected final JLabel messageLabel = new JLabel(" ");

    /** Shows a message in green (done) or red (something went wrong). " " clears it. */
    protected void showMessage(String text, boolean isError) {
        messageLabel.setForeground(isError ? Theme.DANGER : Theme.MINT);
        messageLabel.setText(text);
    }

    /** Reload this screen's data. Called each time the screen is shown. */
    public abstract void onShow();

    /** Login and Household Setup return false to hide the sidebar. */
    public boolean showsSidebar() {
        return true;
    }

    /** Login returns false; every other screen needs someone logged in. */
    public boolean requiresLogin() {
        return true;
    }
}
