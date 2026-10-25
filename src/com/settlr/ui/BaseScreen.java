package com.settlr.ui;

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
