package com.settlr.ui;

import com.settlr.util.Session;

import javax.swing.JPanel;
import java.awt.CardLayout;
import java.util.HashMap;
import java.util.Map;

/**
 * Switches between screens. There is only one Navigator (singleton):
 *     Navigator.getInstance().show(Navigator.TASKS);
 *
 * It uses a CardLayout: every screen is a "card" in one stack, and only
 * the top card is visible.
 */
public final class Navigator {

    // Screen names, so nobody mistypes "tasks" as "task"
    public static final String LOGIN = "login";
    public static final String REGISTER = "register";
    public static final String HOUSEHOLD_SETUP = "household-setup";
    public static final String DASHBOARD = "dashboard";
    public static final String TASKS = "tasks";
    public static final String EXPENSES = "expenses";
    public static final String BALANCES = "balances";
    public static final String LEADERBOARD = "leaderboard";
    public static final String ACTIVITY = "activity";
    public static final String SETTINGS = "settings";

    private static Navigator instance;

    private final CardLayout cards = new CardLayout();
    private final JPanel container = new JPanel(cards);
    private final Map<String, BaseScreen> screens = new HashMap<>();
    private Sidebar sidebar;
    private String current;

    private Navigator() { }

    public static synchronized Navigator getInstance() {
        if (instance == null) {
            instance = new Navigator();
        }
        return instance;
    }

    /** The panel that holds all the screens. MainFrame puts it in the window. */
    public JPanel getContainer() {
        return container;
    }

    void setSidebar(Sidebar sidebar) {
        this.sidebar = sidebar;
    }

    /** Adds a screen under a name. Called once per screen by MainFrame. */
    public void register(String name, BaseScreen screen) {
        screens.put(name, screen);
        container.add(screen, name);
    }

    /** The registered screen with this name (null if none). */
    public BaseScreen getScreen(String name) {
        return screens.get(name);
    }

    /** The name of the screen currently showing (null before the first show). */
    public String getCurrent() {
        return current;
    }

    /** Shows the named screen, hides or shows the sidebar, and reloads the screen. */
    public void show(String name) {
        BaseScreen screen = screens.get(name);
        if (screen == null) {
            throw new IllegalArgumentException("No screen named \"" + name + "\". Register it in MainFrame.");
        }
        // Screens that need a logged-in user send you to Login instead
        if (screen.requiresLogin() && !Session.getInstance().isLoggedIn()) {
            name = LOGIN;
            screen = screens.get(LOGIN);
        }

        if (sidebar != null) {
            sidebar.setVisible(screen.showsSidebar());
            sidebar.setSelected(name);
            sidebar.refreshUser();
        }
        cards.show(container, name);
        current = name;

        try {
            screen.onShow(); // each screen reloads in its own way (polymorphism)
        } catch (RuntimeException e) {
            // A bug in one screen shouldn't freeze the whole app; print it for whoever is debugging
            System.err.println("Screen \"" + name + "\" failed to load:");
            e.printStackTrace();
        }
    }
}
