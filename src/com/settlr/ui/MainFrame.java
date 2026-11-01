package com.settlr.ui;

import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.screens.ActivityFeedScreen;
import com.settlr.ui.screens.ExpensesScreen;
import com.settlr.ui.screens.HouseholdSetupScreen;
import com.settlr.ui.screens.LeaderboardScreen;
import com.settlr.ui.screens.LoginScreen;
import com.settlr.ui.screens.PlaceholderScreen;
import com.settlr.ui.screens.ProfileScreen;
import com.settlr.ui.screens.RegisterScreen;
import com.settlr.ui.screens.TaskFormDialog;
import com.settlr.util.DateTimeUtil;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * The only window in Settlr: the sidebar on the left, the top bar (your avatar) and the
 * current screen on the right.
 * To add a new screen: create it (extends BaseScreen), then register it below.
 */
public class MainFrame extends JFrame {

    public MainFrame() {
        super("Settlr");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 700);
        setMinimumSize(new Dimension(900, 600));
        setLocationRelativeTo(null);

        Navigator nav = Navigator.getInstance();
        Sidebar sidebar = new Sidebar();
        nav.setSidebar(sidebar);

        nav.register(Navigator.LOGIN, new LoginScreen());
        nav.register(Navigator.REGISTER, new RegisterScreen());
        nav.register(Navigator.HOUSEHOLD_SETUP, new HouseholdSetupScreen());
        nav.register(Navigator.EXPENSES, new ExpensesScreen()); // includes the old Balances page
        nav.register(Navigator.LEADERBOARD, new LeaderboardScreen());
        nav.register(Navigator.ACTIVITY, new ActivityFeedScreen());

        // Not built yet: replace each placeholder with the real screen when it's merged
        nav.register(Navigator.DASHBOARD, new PlaceholderScreen("Home"));
        // Tasks: real screen not built yet (#13), but its "Add tasks" popup can be tried already
        RoundedButton addTask = RoundedButton.mint("+  Add tasks");
        addTask.setArc(RoundedButton.PILL);
        PlaceholderScreen tasks = new PlaceholderScreen("Tasks", addTask);
        addTask.addActionListener(e -> TaskFormDialog.open(tasks, task -> tasks.setNote(
                "Saved \"" + task.getTitle() + "\", due " + DateTimeUtil.format(task.getFirstDueAt()) + ".")));
        nav.register(Navigator.TASKS, tasks);
        nav.register(Navigator.SETTINGS, new PlaceholderScreen("Household settings"));
        nav.register(Navigator.PROFILE, new ProfileScreen()); // opens from the avatar in the top bar

        TopBar topBar = new TopBar();
        nav.setTopBar(topBar);
        JPanel content = new JPanel(new BorderLayout()); // top bar above the current screen
        content.add(topBar, BorderLayout.NORTH);
        content.add(nav.getContainer(), BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(sidebar, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
    }
}
