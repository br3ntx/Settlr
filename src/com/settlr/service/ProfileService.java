package com.settlr.service;

import com.settlr.dao.DaoFactory;
import com.settlr.dao.HouseholdDao;
import com.settlr.dao.MembershipDao;
import com.settlr.dao.UserDao;
import com.settlr.exception.AuthException;
import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.exception.ValidationException;
import com.settlr.model.Household;
import com.settlr.model.PayMethod;
import com.settlr.model.Role;
import com.settlr.model.User;
import com.settlr.util.PasswordHasher;
import com.settlr.util.Session;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Everything on the Profile page: display name, username, password, profile picture and
 * "How to pay me". Every change is saved for the logged-in user only, and the Session's
 * user is updated right after, so the sidebar and other screens show the new values.
 */
public class ProfileService {

    private final Session session = Session.getInstance();

    /** Where profile pictures are saved, relative to the project folder (git ignores uploads/). */
    public static final String AVATAR_FOLDER = "uploads/avatars";
    private static final long MAX_PHOTO_BYTES = 5L * 1024 * 1024;
    private static final int PHOTO_SIZE = 256; // saved as a 256 x 256 square

    /** The household card on the Profile page. Read-only. */
    public static final class HouseholdInfo {
        private final Household household;
        private final Role role;
        private final int memberCount;

        HouseholdInfo(Household household, Role role, int memberCount) {
            this.household = household;
            this.role = role;
            this.memberCount = memberCount;
        }

        public Household getHousehold() { return household; }
        public Role getRole() { return role; }
        public int getMemberCount() { return memberCount; }
    }

    private final UserDao userDao = DaoFactory.get().users();
    private final HouseholdDao householdDao = DaoFactory.get().households();
    private final MembershipDao membershipDao = DaoFactory.get().memberships();

    // =====================================================================
    //  Reading
    // =====================================================================

    /** The logged-in user, freshly loaded from the database. */
    public User getMe() throws SettlrException {
        User fresh = userDao.findById(session.requireUser().getId());
        if (fresh == null) {
            throw new AuthException("Your account could not be found. Please log in again.");
        }
        session.requireUser().copyFrom(fresh);
        return session.requireUser();
    }

    /** Your household, your role and how many people are in it, or null if you're not in one yet. */
    public HouseholdInfo getHouseholdInfo() throws SettlrException {
        Session session = Session.getInstance();
        if (session.getCurrentHousehold() == null) {
            return null;
        }
        // Reloaded so a new invite code made by the admin shows up
        Household h = householdDao.findById(session.getCurrentHousehold().getId());
        if (h == null) {
            return null;
        }
        int count = membershipDao.findActiveMembers(h.getId()).size();
        return new HouseholdInfo(h, session.getRole(), count);
    }

    // =====================================================================
    //  Changes
    // =====================================================================

    public void changeDisplayName(String displayName) throws SettlrException {
        User u = editableCopy();
        try {
            u.setDisplayName(displayName == null ? "" : displayName);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
        save(u);
    }

    /**
     * Changes the username you log in with. Asks for your current password first, so someone
     * using your unlocked computer can't do it. Everything else in Settlr uses your user id,
     * so your tasks, expenses and payments stay yours.
     */
    public void changeUsername(String newUsername, String currentPassword) throws SettlrException {
        User u = editableCopy();
        checkPassword(u, currentPassword);
        String name = newUsername == null ? "" : newUsername.trim();
        if (name.equals(u.getUsername())) {
            throw new ValidationException("That's already your username.");
        }
        try {
            u.setUsername(name);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
        User owner = userDao.findByUsername(name); // not case-sensitive
        if (owner != null && owner.getId() != u.getId()) {
            throw new ValidationException("That username is already taken.");
        }
        save(u);
    }

    public void changePassword(String currentPassword, String newPassword, String confirm) throws SettlrException {
        User u = editableCopy();
        checkPassword(u, currentPassword);
        if (newPassword == null || newPassword.length() < AuthService.MIN_PASSWORD_LENGTH) {
            throw new ValidationException("New password must be at least " + AuthService.MIN_PASSWORD_LENGTH
                    + " characters.");
        }
        if (!newPassword.equals(confirm)) {
            throw new ValidationException("New passwords don't match.");
        }
        if (PasswordHasher.matches(newPassword, u.getPasswordHash())) {
            throw new ValidationException("That's the same as your current password.");
        }
        u.setPasswordHash(PasswordHasher.hash(newPassword));
        save(u);
    }

    /** Sets "How to pay me". A null method clears it. */
    public void setPaymentInfo(PayMethod method, String number, String name) throws SettlrException {
        User u = editableCopy();
        try {
            u.setPaymentInfo(method, number, name);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }
        save(u);
    }

    /**
     * Uses an image file as your profile picture. It's cropped to a square, shrunk to
     * 256 x 256 and saved as a PNG in uploads/avatars. The old picture is deleted.
     */
    public void changePhoto(File image) throws SettlrException {
        if (image == null || !image.isFile()) {
            throw new ValidationException("Please pick an image file.");
        }
        if (image.length() > MAX_PHOTO_BYTES) {
            throw new ValidationException("That image is too big. Please pick one under 5 MB.");
        }
        BufferedImage source;
        try {
            source = ImageIO.read(image);
        } catch (IOException e) {
            source = null;
        }
        if (source == null) {
            throw new ValidationException("That file isn't a picture Settlr can open. Try a JPG or PNG.");
        }

        User u = editableCopy();
        Path folder = Paths.get(AVATAR_FOLDER);
        Path target = folder.resolve("user_" + u.getId() + "_" + System.currentTimeMillis() + ".png");
        try {
            Files.createDirectories(folder);
            ImageIO.write(squareThumbnail(source), "png", target.toFile());
        } catch (IOException e) {
            throw new DataAccessException("Could not save the picture.", e);
        }
        String old = u.getAvatarPath();
        u.setAvatarPath(AVATAR_FOLDER + "/" + target.getFileName());
        try {
            save(u);
        } catch (SettlrException e) {
            deleteQuietly(target.toString()); // don't leave a file nobody points to
            throw e;
        }
        deleteQuietly(old);
    }

    /** Goes back to the letter avatar. */
    public void removePhoto() throws SettlrException {
        User u = editableCopy();
        String old = u.getAvatarPath();
        if (old == null) {
            return;
        }
        u.setAvatarPath(null);
        save(u);
        deleteQuietly(old);
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    /** A fresh copy of the logged-in user to change; the Session is only updated after a save works. */
    private User editableCopy() throws SettlrException {
        User fresh = userDao.findById(session.requireUser().getId());
        if (fresh == null) {
            throw new AuthException("Your account could not be found. Please log in again.");
        }
        return fresh;
    }

    private void save(User u) throws SettlrException {
        userDao.update(u);
        session.requireUser().copyFrom(u);
    }

    private static void checkPassword(User u, String password) throws ValidationException {
        if (password == null || password.isEmpty()) {
            throw new ValidationException("Please enter your current password.");
        }
        if (!PasswordHasher.matches(password, u.getPasswordHash())) {
            throw new ValidationException("Your current password is incorrect.");
        }
    }

    /** The middle square of the picture, scaled to PHOTO_SIZE. */
    private static BufferedImage squareThumbnail(BufferedImage src) {
        int side = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - side) / 2, y = (src.getHeight() - side) / 2;
        BufferedImage out = new BufferedImage(PHOTO_SIZE, PHOTO_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, PHOTO_SIZE, PHOTO_SIZE, x, y, x + side, y + side, null);
        g.dispose();
        return out;
    }

    /** Deletes an old picture, but only inside uploads/avatars. */
    private static void deleteQuietly(String path) {
        if (path == null || !path.replace('\\', '/').startsWith(AVATAR_FOLDER + "/")) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(path));
        } catch (IOException ignored) {
            // an old file left behind is harmless
        }
    }
}
