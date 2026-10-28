package com.settlr.ui;

import com.settlr.model.Household;
import com.settlr.model.User;
import com.settlr.service.AuthService;
import com.settlr.ui.theme.Theme;
import com.settlr.util.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Left-hand navigation: logo, who is logged in, one button per screen and Log out.
 * The button for the screen being shown is highlighted.
 */
public class Sidebar extends JPanel {

    private final Map<String, NavButton> items = new LinkedHashMap<>();
    private final JLabel userLabel = new JLabel(" ");

    public Sidebar() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Theme.SIDEBAR);
        setBorder(new EmptyBorder(24, 16, 20, 16));
        setPreferredSize(new Dimension(210, 0));

        // Logo + name
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        brand.setOpaque(false);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        ImageIcon logo = new ImageIcon("resources/images/SETTLR LOGO.png");
        if (logo.getIconWidth() > 0) {
            JLabel logoLabel = new JLabel(new ImageIcon(
                    logo.getImage().getScaledInstance(30, 30, Image.SCALE_SMOOTH)));
            logoLabel.setBorder(new EmptyBorder(0, 0, 0, 8));
            brand.add(logoLabel);
        }
        JLabel name = new JLabel("SETTLR");
        name.setFont(Theme.font(Font.BOLD, 20));
        name.setForeground(Theme.TEXT);
        brand.add(name);
        add(brand);

        // "Ana  \u2022  Room 4B"
        userLabel.setFont(Theme.font(Font.PLAIN, 11));
        userLabel.setForeground(Theme.MUTED_TEXT);
        userLabel.setBorder(new EmptyBorder(4, 2, 0, 0));
        userLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(userLabel);
        add(Box.createVerticalStrut(20));

        addItem("Home", Navigator.DASHBOARD);
        addItem("Tasks", Navigator.TASKS);
        addItem("Expenses", Navigator.EXPENSES);
        addItem("Leaderboard", Navigator.LEADERBOARD);
        addItem("Activity", Navigator.ACTIVITY);
        addItem("Household", Navigator.SETTINGS);

        add(Box.createVerticalGlue()); // pushes Log out to the bottom

        NavButton logout = new NavButton("Log out");
        logout.addActionListener(e -> {
            new AuthService().logout();
            Navigator.getInstance().show(Navigator.LOGIN);
        });
        add(logout);
    }

    private void addItem(String label, String screenName) {
        NavButton button = new NavButton(label);
        button.addActionListener(e -> Navigator.getInstance().show(screenName));
        items.put(screenName, button);
        add(button);
        add(Box.createVerticalStrut(4));
    }

    /** Highlights the button for the screen being shown. */
    public void setSelected(String screenName) {
        items.forEach((name, button) -> button.setSelected(name.equals(screenName)));
    }

    /** Updates the "name  \u2022  household" line from the Session. */
    public void refreshUser() {
        User user = Session.getInstance().getCurrentUser();
        Household household = Session.getInstance().getCurrentHousehold();
        if (user == null) {
            userLabel.setText(" ");
        } else if (household == null) {
            userLabel.setText(user.getDisplayName());
        } else {
            userLabel.setText(user.getDisplayName() + "  \u2022  " + household.getName());
        }
    }

    /** A flat sidebar button; the selected one gets a pill in the primary color. */
    private static class NavButton extends JButton {
        private boolean selected;

        NavButton(String text) {
            super(text);
            setFont(Theme.font(Font.BOLD, 14));
            setForeground(Theme.TEXT);
            setHorizontalAlignment(SwingConstants.LEFT);
            setBorder(new EmptyBorder(8, 12, 8, 12));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        public void setSelected(boolean selected) {
            this.selected = selected;
            setForeground(selected ? Theme.ON_PRIMARY : Theme.TEXT);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape pill = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 18, 18);
            if (selected) {
                g2.setColor(Theme.PRIMARY);
                g2.fill(pill);
            } else if (getModel().isRollover()) {
                g2.setColor(new Color(Theme.TEXT.getRed(), Theme.TEXT.getGreen(), Theme.TEXT.getBlue(), 20));
                g2.fill(pill);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
