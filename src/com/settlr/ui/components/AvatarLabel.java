package com.settlr.ui.components;

import com.settlr.model.User;
import com.settlr.ui.theme.Theme;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Round avatar: the person's profile picture, or the first letter of their name when they
 * have none (or the picture file is missing on this computer). Also used with an expense
 * title to show its first letter.
 */
public class AvatarLabel extends JComponent {

    /** Pictures already read from disk, by path. Each upload gets a new file name, so this never goes stale. */
    private static final Map<String, BufferedImage> PHOTOS = new HashMap<>();

    private final int size;
    private String letter = "?";
    private BufferedImage photo;

    /** Letter only, e.g. for an expense or a name without a User. */
    public AvatarLabel(String name, int size) {
        this.size = size;
        setLetter(name);
        Dimension d = new Dimension(size, size);
        setPreferredSize(d);
        setMinimumSize(d);
        setMaximumSize(d);
    }

    /** The user's picture, or their initial. */
    public AvatarLabel(User user, int size) {
        this(user == null ? null : user.getDisplayName(), size);
        setUser(user);
    }

    /** Switches to another user (or the same user after they changed their name or picture). */
    public void setUser(User user) {
        setLetter(user == null ? null : user.getDisplayName());
        photo = user == null ? null : loadPhoto(user.getAvatarPath());
        repaint();
    }

    private void setLetter(String name) {
        letter = name == null || name.isBlank() ? "?" : name.trim().substring(0, 1).toUpperCase();
    }

    private static BufferedImage loadPhoto(String path) {
        if (path == null) {
            return null;
        }
        if (!PHOTOS.containsKey(path)) {
            BufferedImage img = null;
            File file = new File(path);
            if (file.isFile()) {
                try {
                    img = ImageIO.read(file);
                } catch (IOException ignored) {
                    // unreadable picture: fall back to the letter
                }
            }
            PHOTOS.put(path, img);
        }
        return PHOTOS.get(path);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int y = (getHeight() - size) / 2;
        Shape circle = new Ellipse2D.Float(0, y, size, size);
        if (photo != null) {
            g2.setClip(circle);
            g2.drawImage(photo, 0, y, size, size, null);
        } else {
            g2.setColor(new Color(255, 255, 255, 45));
            g2.fill(circle);
            g2.setFont(Theme.font(Font.BOLD, Math.max(11, size * 2 / 5)));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.TEXT);
            g2.drawString(letter, (size - fm.stringWidth(letter)) / 2,
                    y + (size - fm.getHeight()) / 2 + fm.getAscent());
        }
        g2.dispose();
    }
}
