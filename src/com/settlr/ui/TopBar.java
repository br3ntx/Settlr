package com.settlr.ui;

import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.theme.Theme;
import com.settlr.util.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;

/**
 * The strip above every logged-in screen with your avatar on the right.
 * Clicking the avatar opens your Profile. It gets a beige ring while you're on Profile.
 */
public class TopBar extends JPanel {

    private static final int AVATAR = 36, RING = 4;

    private final AvatarLabel avatar = new AvatarLabel((String) null, AVATAR);
    private boolean onProfile;
    private boolean hover;

    public TopBar() {
        setLayout(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        setBackground(Theme.BACKGROUND);
        setBorder(new EmptyBorder(12, 0, 0, 28));

        JPanel button = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                if (!onProfile && !hover) {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(onProfile ? Theme.PRIMARY : new Color(232, 217, 205, 110));
                g2.setStroke(new BasicStroke(2.2f));
                g2.draw(new Ellipse2D.Float(1.2f, 1.2f, getWidth() - 2.4f, getHeight() - 2.4f));
                g2.dispose();
            }
        };
        int outer = AVATAR + RING * 2;
        button.setOpaque(false);
        button.setPreferredSize(new Dimension(outer, outer));
        avatar.setBounds(RING, RING, AVATAR, AVATAR);
        button.add(avatar);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText("Your profile");
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { Navigator.getInstance().show(Navigator.PROFILE); }
            @Override public void mouseEntered(MouseEvent e) { hover = true; button.repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; button.repaint(); }
        };
        button.addMouseListener(mouse);
        avatar.addMouseListener(mouse); // the avatar sits on top, so it needs the same listener
        add(button);
    }

    /** Shows the logged-in user's picture or initial, and the ring when the Profile is showing. */
    public void refresh(String currentScreen) {
        avatar.setUser(Session.getInstance().getCurrentUser());
        onProfile = Navigator.PROFILE.equals(currentScreen);
        repaint();
    }
}
