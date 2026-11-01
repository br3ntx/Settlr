package com.settlr.ui.screens;

import com.settlr.exception.SettlrException;
import com.settlr.model.Household;
import com.settlr.model.PayMethod;
import com.settlr.model.Role;
import com.settlr.model.User;
import com.settlr.service.AuthService;
import com.settlr.service.ProfileService;
import com.settlr.ui.BaseScreen;
import com.settlr.ui.Navigator;
import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.SlimScrollBarUI;
import com.settlr.ui.components.StyledComboBox;
import com.settlr.ui.components.StyledPasswordField;
import com.settlr.ui.components.StyledTextField;
import com.settlr.ui.theme.Theme;
import com.settlr.util.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Your profile (opens from the avatar in the top bar):
 *   - header: picture (click to change), display name, @username, household, join date
 *   - Account: display name, username (asks for your password), password
 *   - How to pay me: GCash / Maya / bank / cash, shown to housemates when they settle up
 *   - Household: name, your role, invite code with Copy, link to the Household page
 *   - Log out
 * The rules live in ProfileService; this screen only shows them.
 */
public class ProfileScreen extends BaseScreen {

    private static final DateTimeFormatter JOINED = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final String DOT = "  \u00B7  ";
    private static final Color DIVIDER = new Color(232, 217, 205, 45);

    /** "Not set" plus every PayMethod, for the dropdown in "How to pay me". */
    private enum PayChoice {
        NONE(null), GCASH(PayMethod.GCASH), MAYA(PayMethod.MAYA), BANK(PayMethod.BANK), CASH(PayMethod.CASH);

        final PayMethod method;

        PayChoice(PayMethod method) {
            this.method = method;
        }

        String label() {
            return method == null ? "Not set" : method.getLabel();
        }

        static PayChoice of(PayMethod m) {
            for (PayChoice c : values()) {
                if (c.method == m) {
                    return c;
                }
            }
            return NONE;
        }
    }

    private final ProfileService profileService = new ProfileService();
    private final JPanel content = vertical();
    private final JLabel messageLabel = new JLabel(" ");

    public ProfileScreen() {
        setBackground(Theme.BACKGROUND);
        setLayout(new BorderLayout());
        content.setBorder(new EmptyBorder(0, 40, 24, 40));

        JPanel holder = new JPanel(new BorderLayout()); // keeps everything at the top
        holder.setOpaque(false);
        holder.add(content, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        SlimScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);

        messageLabel.setFont(Theme.font(Font.PLAIN, 12));
        messageLabel.setBorder(new EmptyBorder(10, 2, 0, 0));
    }

    @Override
    public void onShow() {
        showMessage(" ", false);
        rebuild();
    }

    /** Builds the whole page again from fresh data (cheap: it's a handful of components). */
    private void rebuild() {
        content.removeAll();
        User me;
        ProfileService.HouseholdInfo info;
        try {
            me = profileService.getMe();
            info = profileService.getHouseholdInfo();
        } catch (SettlrException e) {
            content.add(left(label("Profile", Font.BOLD, 30, Theme.PRIMARY)));
            content.add(left(label(e.getMessage(), Font.PLAIN, 13, Theme.DANGER)));
            content.revalidate();
            content.repaint();
            return;
        }

        content.add(left(label("Profile", Font.BOLD, 30, Theme.PRIMARY)));
        content.add(Box.createVerticalStrut(14));
        content.add(left(buildHeader(me, info)));
        content.add(left(messageLabel));

        JPanel leftColumn = vertical();
        leftColumn.add(left(buildAccountCard(me)));
        leftColumn.add(Box.createVerticalStrut(16));
        leftColumn.add(left(buildPayCard(me)));

        JPanel rightColumn = vertical();
        rightColumn.add(left(buildHouseholdCard(info)));
        rightColumn.add(Box.createVerticalStrut(16));
        rightColumn.add(left(buildLogout()));

        JPanel columns = new JPanel(new GridBagLayout());
        columns.setOpaque(false);
        columns.setBorder(new EmptyBorder(14, 0, 0, 0));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.NORTH;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.weightx = 1.25;
        c.insets = new Insets(0, 0, 0, 18);
        columns.add(narrow(leftColumn), c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        columns.add(narrow(rightColumn), c);
        content.add(left(columns));

        Navigator.getInstance().refreshChrome(); // sidebar name and top-bar avatar
        content.revalidate();
        content.repaint();
    }

    // =====================================================================
    //  Header
    // =====================================================================

    private JPanel buildHeader(User me, ProfileService.HouseholdInfo info) {
        JPanel photo = new PhotoButton(me);

        List<String> facts = new ArrayList<>();
        facts.add("@" + me.getUsername());
        if (info != null) {
            facts.add(info.getHousehold().getName());
        }
        facts.add("Joined " + JOINED.format(me.getCreatedAt()));

        JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        links.setOpaque(false);
        links.add(link("Change photo", this::pickPhoto));
        if (me.getAvatarPath() != null) {
            links.add(label(DOT, Font.PLAIN, 12, Theme.MUTED_TEXT));
            links.add(link("Remove", this::removePhoto));
        }

        JPanel text = vertical();
        text.add(left(label(me.getDisplayName(), Font.BOLD, 26, Theme.PRIMARY)));
        text.add(Box.createVerticalStrut(3));
        text.add(left(label(String.join(DOT, facts), Font.PLAIN, 13, Theme.MUTED_TEXT)));
        text.add(Box.createVerticalStrut(8));
        text.add(left(links));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        header.setOpaque(false);
        header.add(photo);
        header.add(Box.createHorizontalStrut(20));
        JPanel textHolder = new JPanel(new GridBagLayout()); // centers the text next to the picture
        textHolder.setOpaque(false);
        textHolder.add(text);
        textHolder.setPreferredSize(new Dimension(text.getPreferredSize().width, PhotoButton.SIZE + 4));
        header.add(textHolder);
        return header;
    }

    /** The big picture with a small camera badge; clicking it changes the photo. */
    private class PhotoButton extends JPanel {
        private static final int SIZE = 84;

        PhotoButton(User me) {
            super(null);
            setOpaque(false);
            setPreferredSize(new Dimension(SIZE + 4, SIZE + 4));
            AvatarLabel avatar = new AvatarLabel(me, SIZE);
            avatar.setBounds(0, 0, SIZE, SIZE);
            add(avatar);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Change photo");
            MouseAdapter click = new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { pickPhoto(); }
            };
            addMouseListener(click);
            avatar.addMouseListener(click);
        }

        @Override
        protected void paintChildren(Graphics g) {
            super.paintChildren(g);
            // camera badge on top of the avatar, bottom right
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int d = 28, x = SIZE + 4 - d, y = SIZE + 4 - d;
            g2.setColor(Theme.BACKGROUND);
            g2.fill(new Ellipse2D.Float(x - 3, y - 3, d + 6, d + 6));
            g2.setColor(Theme.PRIMARY);
            g2.fill(new Ellipse2D.Float(x, y, d, d));
            g2.setColor(Theme.ON_PRIMARY);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float cx = x + d / 2f, cy = y + d / 2f;
            g2.draw(new RoundRectangle2D.Float(cx - 7, cy - 4, 14, 10, 3, 3));    // body
            g2.draw(new RoundRectangle2D.Float(cx - 3, cy - 7, 6, 3, 1.5f, 1.5f)); // top bump
            g2.draw(new Ellipse2D.Float(cx - 2.6f, cy - 1.6f, 5.2f, 5.2f));        // lens
            g2.dispose();
        }
    }

    // =====================================================================
    //  Cards
    // =====================================================================

    private GlassCard buildAccountCard(User me) {
        GlassCard card = card("Account", null);
        card.add(left(fieldRow("Display name", me.getDisplayName(), null,
                smallButton("Edit", this::editDisplayName), true)));
        card.add(left(fieldRow("Username", "@" + me.getUsername(), "Used to log in.",
                smallButton("Edit", this::editUsername), true)));
        card.add(left(fieldRow("Password", "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022", null,
                smallButton("Change password", this::changePassword), false)));
        return card;
    }

    private GlassCard buildPayCard(User me) {
        PayMethod m = me.getPayMethod();
        GlassCard card = card("How to pay me", smallButton(m == null ? "Add" : "Edit", this::editPayment));
        card.add(left(label("Shown to housemates when they settle up with you.", Font.PLAIN, 12,
                Theme.MUTED_TEXT)));
        card.add(Box.createVerticalStrut(10));
        if (m == null) {
            card.add(left(label("<html><div style='width:300px'>Not set yet. Add your GCash, Maya or bank "
                    + "details so housemates know where to send money.</div></html>", Font.PLAIN, 12, Theme.TEXT)));
        } else {
            JPanel line = new JPanel(new BorderLayout(12, 0));
            line.setOpaque(false);
            line.add(new MethodTile(m, 36), BorderLayout.WEST);
            JPanel text = vertical();
            if (m.needsAccount()) {
                text.add(left(label(m.getLabel() + " \u00B7 " + me.getPayNumber(), Font.BOLD, 14, Theme.TEXT)));
                text.add(left(label(me.getPayName(), Font.PLAIN, 12, Theme.MUTED_TEXT)));
            } else {
                text.add(left(label("Cash only", Font.BOLD, 14, Theme.TEXT)));
                text.add(left(label("Pay me in person.", Font.PLAIN, 12, Theme.MUTED_TEXT)));
            }
            line.add(text, BorderLayout.CENTER);
            card.add(left(fitHeight(line)));
        }
        card.add(Box.createVerticalStrut(4));
        return card;
    }

    private GlassCard buildHouseholdCard(ProfileService.HouseholdInfo info) {
        GlassCard card = card("Household", null);
        if (info == null) {
            card.add(left(label("You're not in a household yet.", Font.PLAIN, 13, Theme.TEXT)));
            card.add(Box.createVerticalStrut(12));
            card.add(left(link("Set up a household \u203A",
                    () -> Navigator.getInstance().show(Navigator.HOUSEHOLD_SETUP))));
            card.add(Box.createVerticalStrut(6));
            return card;
        }
        Household h = info.getHousehold();
        String role = info.getRole() == Role.ADMIN ? "Admin" : "Member";
        card.add(left(fieldRow(h.getName(), null, role + " \u00B7 " + info.getMemberCount()
                + (info.getMemberCount() == 1 ? " person" : " people"), null, true)));

        JLabel code = label(spaced(h.getInviteCode()), Font.BOLD, 20, Theme.PRIMARY);
        RoundedButton copy = smallButton("Copy", null);
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(h.getInviteCode()), null);
            copy.setText("Copied!");
            Timer back = new Timer(1500, ev -> copy.setText("Copy"));
            back.setRepeats(false);
            back.start();
        });
        JPanel codeLine = new JPanel(new BorderLayout());
        codeLine.setOpaque(false);
        codeLine.add(code, BorderLayout.WEST);
        codeLine.add(copy, BorderLayout.EAST);

        card.add(Box.createVerticalStrut(12));
        card.add(left(label("Invite code", Font.PLAIN, 11, Theme.MUTED_TEXT)));
        card.add(Box.createVerticalStrut(4));
        card.add(left(fitHeight(codeLine)));
        card.add(Box.createVerticalStrut(4));
        card.add(left(label("Share it so roommates can join.", Font.PLAIN, 12, Theme.MUTED_TEXT)));
        card.add(Box.createVerticalStrut(14));
        card.add(left(link("Go to Household \u203A", () -> Navigator.getInstance().show(Navigator.SETTINGS))));
        card.add(Box.createVerticalStrut(6));
        return card;
    }

    private JComponent buildLogout() {
        RoundedButton logout = RoundedButton.outline("Log out");
        logout.setOutline(new Color(224, 96, 90, 170));
        logout.setForeground(Theme.DANGER);
        logout.setArc(RoundedButton.PILL);
        logout.setFont(Theme.font(Font.BOLD, 13));
        logout.addActionListener(e -> {
            new AuthService().logout();
            Navigator.getInstance().show(Navigator.LOGIN);
        });
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(logout, BorderLayout.CENTER);
        holder.setPreferredSize(new Dimension(10, 40));
        return fitHeight(holder);
    }

    // =====================================================================
    //  Actions (each opens a small popup)
    // =====================================================================

    private void editDisplayName() {
        User me = currentUser();
        FormDialog d = new FormDialog("Edit display name", "This is the name your housemates see.");
        StyledTextField name = d.addField("Display name", new StyledTextField("e.g. Ben"));
        name.setText(me.getDisplayName());
        d.onSave("Save", () -> profileService.changeDisplayName(name.getText()),
                () -> done("Display name updated."));
        d.showOver(this);
    }

    private void editUsername() {
        User me = currentUser();
        FormDialog d = new FormDialog("Change username",
                "You'll log in with the new username from now on. Your tasks and expenses stay yours.");
        StyledTextField name = d.addField("New username", new StyledTextField("3 to 30 letters, numbers or _"));
        name.setText(me.getUsername());
        StyledPasswordField password = d.addField("Current password", new StyledPasswordField("To confirm it's you"));
        d.onSave("Save", () -> profileService.changeUsername(name.getText(), new String(password.getPassword())),
                () -> done("Username changed. Log in as @" + currentUser().getUsername() + " from now on."));
        d.showOver(this);
    }

    private void changePassword() {
        FormDialog d = new FormDialog("Change password", null);
        StyledPasswordField current = d.addField("Current password", new StyledPasswordField(""));
        StyledPasswordField next = d.addField("New password",
                new StyledPasswordField("At least " + AuthService.MIN_PASSWORD_LENGTH + " characters"));
        StyledPasswordField confirm = d.addField("Confirm new password", new StyledPasswordField(""));
        d.onSave("Change password", () -> profileService.changePassword(new String(current.getPassword()),
                new String(next.getPassword()), new String(confirm.getPassword())),
                () -> done("Password changed."));
        d.showOver(this);
    }

    private void editPayment() {
        User me = currentUser();
        FormDialog d = new FormDialog("How to pay me", "Housemates see this when they settle up with you.");
        StyledComboBox<PayChoice> method = d.addField("Method",
                new StyledComboBox<>(Arrays.asList(PayChoice.values()), PayChoice::label));
        method.setFont(Theme.font(Font.PLAIN, 13));
        StyledTextField number = d.addField("Number", new StyledTextField("e.g. 0917 123 4567"));
        StyledTextField name = d.addField("Account name", new StyledTextField("Name on the account"));
        JLabel hint = d.addHint(" ");
        method.setSelectedItem(PayChoice.of(me.getPayMethod()));
        number.setText(me.getPayNumber() == null ? "" : me.getPayNumber());
        name.setText(me.getPayName() == null ? "" : me.getPayName());

        Runnable sync = () -> {
            PayChoice c = method.getSelected();
            boolean account = c != null && c.method != null && c.method.needsAccount();
            d.setFieldVisible(number, account);
            d.setFieldVisible(name, account);
            if (account) {
                d.setFieldLabel(number, c.method.getNumberLabel());
            }
            hint.setText(c == null || c.method == null ? "Housemates won't see any payment details."
                    : account ? " " : "Housemates will see that you take cash.");
        };
        method.addActionListener(e -> sync.run());
        sync.run();
        d.onSave("Save", () -> {
            PayChoice c = method.getSelected();
            profileService.setPaymentInfo(c == null ? null : c.method, number.getText(), name.getText());
        }, () -> done("Payment details saved."));
        d.showOver(this);
    }

    private void pickPhoto() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a profile picture");
        chooser.setFileFilter(new FileNameExtensionFilter("Pictures (JPG, PNG, GIF, BMP)",
                "jpg", "jpeg", "png", "gif", "bmp"));
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            profileService.changePhoto(chooser.getSelectedFile());
            done("Profile picture updated.");
        } catch (SettlrException e) {
            showMessage(e.getMessage(), true);
        }
    }

    private void removePhoto() {
        try {
            profileService.removePhoto();
            done("Profile picture removed.");
        } catch (SettlrException e) {
            showMessage(e.getMessage(), true);
        }
    }

    private void done(String message) {
        rebuild();
        showMessage(message, false);
    }

    private void showMessage(String text, boolean isError) {
        messageLabel.setForeground(isError ? Theme.DANGER : Theme.MINT);
        messageLabel.setText(text);
    }

    private static User currentUser() {
        return Session.getInstance().getCurrentUser();
    }

    // =====================================================================
    //  Small pieces
    // =====================================================================

    /** A card with a title (and an optional button on the right of the title). */
    private static GlassCard card(String title, JComponent titleButton) {
        GlassCard card = new GlassCard(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(4, 20, 14, 20));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(12, 0, 6, 0));
        head.add(label(title, Font.BOLD, 15, Theme.TEXT), BorderLayout.WEST);
        if (titleButton != null) {
            head.add(titleButton, BorderLayout.EAST);
        }
        card.add(left(fitHeight(head)));
        return card;
    }

    /** "Display name / Ben / (hint)" on the left and a button on the right, with a line under it. */
    private static JPanel fieldRow(String labelText, String value, String hint, JComponent button, boolean line) {
        JPanel text = vertical();
        text.add(left(label(labelText, Font.PLAIN, 11, Theme.MUTED_TEXT)));
        if (value != null) {
            text.add(Box.createVerticalStrut(3));
            text.add(left(label(value, Font.BOLD, 15, Theme.TEXT)));
        }
        if (hint != null) {
            text.add(Box.createVerticalStrut(2));
            text.add(left(label(hint, value == null ? Font.BOLD : Font.PLAIN, value == null ? 14 : 11,
                    value == null ? Theme.TEXT : Theme.MUTED_TEXT)));
        }
        JPanel row = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                if (line) {
                    g.setColor(DIVIDER);
                    g.fillRect(0, getHeight() - 1, getWidth(), 1);
                }
            }
        };
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(10, 0, 11, 0));
        row.add(text, BorderLayout.CENTER);
        if (button != null) {
            JPanel holder = new JPanel(new GridBagLayout()); // centers the button vertically
            holder.setOpaque(false);
            holder.add(button);
            row.add(holder, BorderLayout.EAST);
        }
        return fitHeight(row);
    }

    private static RoundedButton smallButton(String text, Runnable action) {
        RoundedButton b = RoundedButton.outline(text);
        b.setArc(RoundedButton.PILL);
        b.setFont(Theme.font(Font.BOLD, 12));
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setPreferredSize(new Dimension(b.getFontMetrics(b.getFont()).stringWidth(text) + 30, 30));
        if (action != null) {
            b.addActionListener(e -> action.run());
        }
        return b;
    }

    /** Beige text that acts like a link. */
    private static JLabel link(String text, Runnable action) {
        JLabel l = label(text, Font.BOLD, 12, Theme.PRIMARY);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { action.run(); }
            @Override public void mouseEntered(MouseEvent e) { l.setText("<html><u>" + text + "</u></html>"); }
            @Override public void mouseExited(MouseEvent e) { l.setText(text); }
        });
        return l;
    }

    /** "K7P2QX" as "K 7 P 2 Q X" so it's easy to read out loud. */
    private static String spaced(String code) {
        return String.join(" ", code.split(""));
    }

    private static JPanel vertical() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static <T extends JComponent> T fitHeight(T c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    /** Lets the GridBag weights decide the column widths instead of the content. */
    private static JComponent narrow(JComponent c) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(1, super.getPreferredSize().height);
            }
        };
        p.setOpaque(false);
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    private static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        return l;
    }

    /** The small rounded square with G / M / B / cash sign used for "How to pay me". */
    static final class MethodTile extends JComponent {
        private final String letter;
        private final int size;

        MethodTile(PayMethod m, int size) {
            this.size = size;
            switch (m) {
                case GCASH: letter = "G"; break;
                case MAYA:  letter = "M"; break;
                case BANK:  letter = "B"; break;
                default:    letter = "\u20B1"; break;
            }
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int y = (getHeight() - size) / 2;
            g2.setColor(new Color(95, 208, 176, 40));
            g2.fill(new RoundRectangle2D.Float(0, y, size, size, 10, 10));
            g2.setColor(Theme.MINT);
            g2.setFont(Theme.font(Font.BOLD, size * 2 / 5));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(letter, (size - fm.stringWidth(letter)) / 2, y + (size - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
