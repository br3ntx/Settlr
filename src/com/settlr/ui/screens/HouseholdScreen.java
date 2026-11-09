package com.settlr.ui.screens;

import com.settlr.exception.SettlrException;
import com.settlr.model.Household;
import com.settlr.model.RecurringBill;
import com.settlr.model.Role;
import com.settlr.model.User;
import com.settlr.service.HouseholdService;
import com.settlr.service.SettlementService;
import com.settlr.ui.BaseScreen;
import com.settlr.ui.Navigator;
import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.SlimScrollBarUI;
import com.settlr.ui.components.StatusBadge;
import com.settlr.ui.components.StyledTextField;
import com.settlr.ui.theme.Theme;
import com.settlr.util.MoneyUtil;
import com.settlr.util.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.settlr.ui.components.Ui.*;

/**
 * Household page (sidebar: Household):
 *   - the household's name (Admins can rename it), when it started, who the Admins are
 *   - the invite code with Copy (Admins can make a new one)
 *   - the members with their join date; Admins get a menu to make someone Admin or remove them
 *   - the monthly bills; Admins can pause, resume or delete them
 *   - Leave household (blocked while you still owe money, or while you're the only Admin)
 * Removing someone who owes money takes two confirmations. The rules live in HouseholdService.
 */
public class HouseholdScreen extends BaseScreen {

    private static final String DOT = " \u00B7 ";
    private static final Color DIVIDER = new Color(232, 217, 205, 40);
    private static final DateTimeFormatter JOINED = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final HouseholdService householdService = new HouseholdService();
    private final SettlementService settlementService = new SettlementService();

    private final JLabel crumb = label(" ", Font.PLAIN, 12, Theme.MUTED_TEXT);
    private final JPanel columns = new JPanel(new GridBagLayout());

    public HouseholdScreen() {
        setBackground(Theme.BACKGROUND);
        setLayout(new BorderLayout());

        JPanel page = vertical();
        page.setBorder(new EmptyBorder(0, 40, 24, 40)); // the top bar above already leaves room
        page.add(left(crumb));
        page.add(Box.createVerticalStrut(2));
        page.add(left(label("Household", Font.BOLD, 30, Theme.PRIMARY)));
        messageLabel.setFont(Theme.font(Font.PLAIN, 12));
        messageLabel.setBorder(new EmptyBorder(6, 2, 0, 0));
        page.add(left(messageLabel));
        columns.setOpaque(false);
        columns.setBorder(new EmptyBorder(12, 0, 0, 0));
        page.add(left(columns));

        JPanel holder = new JPanel(new BorderLayout()); // keeps everything at the top
        holder.setOpaque(false);
        holder.add(page, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        SlimScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);
    }

    @Override
    public void onShow() {
        showMessage(" ", false);
        rebuild();
    }

    /** Builds the page again from fresh data. */
    private void rebuild() {
        columns.removeAll();
        Household h = Session.getInstance().getCurrentHousehold();
        crumb.setText((h == null ? "" : h.getName() + DOT) + "Household");
        List<HouseholdService.Member> members;
        List<RecurringBill> bills;
        try {
            members = householdService.getMemberList();
            bills = householdService.getBills();
        } catch (SettlrException e) {
            showMessage(e.getMessage(), true);
            columns.revalidate();
            columns.repaint();
            return;
        }

        JPanel leftColumn = vertical();
        leftColumn.add(left(buildInfoCard(h, members)));
        leftColumn.add(Box.createVerticalStrut(16));
        leftColumn.add(left(buildMembersCard(members)));

        JPanel rightColumn = vertical();
        rightColumn.add(left(buildBillsCard(bills, members)));
        rightColumn.add(Box.createVerticalStrut(16));
        rightColumn.add(left(buildLeave(members)));

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.NORTH;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.weightx = 1.35;
        c.insets = new Insets(0, 0, 0, 18);
        columns.add(narrow(leftColumn), c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        columns.add(narrow(rightColumn), c);

        Navigator.getInstance().refreshChrome(); // the sidebar shows the household name
        columns.revalidate();
        columns.repaint();
    }

    // =====================================================================
    //  Cards
    // =====================================================================

    private GlassCard buildInfoCard(Household h, List<HouseholdService.Member> members) {
        boolean admin = Session.getInstance().isAdmin();
        GlassCard card = card();
        card.setBorder(new EmptyBorder(16, 20, 16, 20));

        JPanel nameLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nameLine.setOpaque(false);
        nameLine.add(label(h.getName(), Font.BOLD, 22, Theme.TEXT));
        if (admin) {
            nameLine.add(Box.createHorizontalStrut(12));
            nameLine.add(smallButton("Edit", this::rename));
        }
        card.add(left(fitHeight(nameLine)));

        List<String> admins = new ArrayList<>();
        for (HouseholdService.Member m : members) {
            if (m.isAdmin()) {
                admins.add(m.getUser().getDisplayName());
            }
        }
        List<String> facts = new ArrayList<>();
        if (!members.isEmpty()) {
            facts.add("Started " + MONTH.format(members.get(0).getJoinedAt()));
        }
        facts.add(members.size() + (members.size() == 1 ? " member" : " members"));
        facts.add((admins.size() == 1 ? "Admin: " : "Admins: ") + String.join(", ", admins));
        card.add(Box.createVerticalStrut(3));
        card.add(left(label(String.join(DOT, facts), Font.PLAIN, 12, Theme.MUTED_TEXT)));
        card.add(Box.createVerticalStrut(14));
        card.add(left(divider()));
        card.add(Box.createVerticalStrut(12));

        card.add(left(label("Invite code", Font.PLAIN, 12, Theme.MUTED_TEXT)));
        JLabel code = label(String.join(" ", h.getInviteCode().split("")), Font.BOLD, 22, Theme.PRIMARY);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        RoundedButton copy = smallButton("Copy", null);
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(h.getInviteCode()), null);
            copy.setText("Copied!");
            Timer back = new Timer(1500, ev -> copy.setText("Copy"));
            back.setRepeats(false);
            back.start();
        });
        buttons.add(copy);
        if (admin) {
            buttons.add(smallButton("New code", this::newCode));
        }
        JPanel codeLine = new JPanel(new BorderLayout());
        codeLine.setOpaque(false);
        codeLine.add(code, BorderLayout.WEST);
        JPanel buttonsHolder = new JPanel(new GridBagLayout());
        buttonsHolder.setOpaque(false);
        buttonsHolder.add(buttons);
        codeLine.add(buttonsHolder, BorderLayout.EAST);
        card.add(Box.createVerticalStrut(4));
        card.add(left(fitHeight(codeLine)));
        card.add(Box.createVerticalStrut(6));
        card.add(left(label("Share it so roommates can join.", Font.PLAIN, 12, Theme.MUTED_TEXT)));
        return card;
    }

    private GlassCard buildMembersCard(List<HouseholdService.Member> members) {
        GlassCard card = card();
        JPanel head = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(12, 0, 4, 0));
        head.add(label("Members", Font.BOLD, 15, Theme.TEXT));
        head.add(label("   " + members.size(), Font.PLAIN, 12, Theme.MUTED_TEXT));
        card.add(left(fitHeight(head)));

        boolean iAmAdmin = Session.getInstance().isAdmin();
        int me = Session.getInstance().getCurrentUser().getId();
        int adminCount = 0;
        for (HouseholdService.Member m : members) {
            if (m.isAdmin()) {
                adminCount++;
            }
        }
        for (int i = 0; i < members.size(); i++) {
            HouseholdService.Member m = members.get(i);
            boolean isMe = m.getUser().getId() == me;
            MoreButton more = null;
            if (iAmAdmin && !isMe) {
                more = new MoreButton();
                if (!m.isAdmin()) {
                    more.item("Make admin", false, () -> changeRole(m, Role.ADMIN));
                } else {
                    more.item("Remove admin", false, () -> changeRole(m, Role.MEMBER));
                }
                more.item("Remove from household", true, () -> remove(m));
            } else if (iAmAdmin && adminCount > 1) {
                more = new MoreButton();
                more.item("Step down as Admin", false, () -> changeRole(m, Role.MEMBER));
            }
            card.add(left(memberRow(m, isMe, more, i < members.size() - 1)));
        }
        card.add(Box.createVerticalStrut(4));
        return card;
    }

    private JPanel memberRow(HouseholdService.Member m, boolean isMe, MoreButton more, boolean line) {
        JPanel nameLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        nameLine.setOpaque(false);
        nameLine.add(label(m.getUser().getDisplayName(), Font.BOLD, 14, Theme.TEXT));
        if (isMe) {
            nameLine.add(label(" (you)", Font.PLAIN, 14, Theme.MUTED_TEXT));
        }
        if (m.isAdmin()) {
            nameLine.add(Box.createHorizontalStrut(8));
            nameLine.add(new StatusBadge("Admin", StatusBadge.Kind.ROLE));
        }
        JPanel text = vertical();
        text.add(left(nameLine));
        text.add(Box.createVerticalStrut(2));
        text.add(left(label("Joined " + JOINED.format(m.getJoinedAt()), Font.PLAIN, 12, Theme.MUTED_TEXT)));
        return row(new AvatarLabel(m.getUser(), 36), text, more, line);
    }

    private GlassCard buildBillsCard(List<RecurringBill> bills, List<HouseholdService.Member> members) {
        GlassCard card = card();
        JPanel head = vertical();
        head.setBorder(new EmptyBorder(12, 0, 4, 0));
        head.add(left(label("Monthly bills", Font.BOLD, 15, Theme.TEXT)));
        head.add(Box.createVerticalStrut(2));
        head.add(left(label("Added to Expenses automatically each month.", Font.PLAIN, 12, Theme.MUTED_TEXT)));
        card.add(left(fitHeight(head)));

        if (bills.isEmpty()) {
            JLabel empty = label("No monthly bills yet.", Font.PLAIN, 13, Theme.MUTED_TEXT);
            empty.setBorder(new EmptyBorder(10, 0, 12, 0));
            card.add(left(empty));
            return card;
        }
        Map<Integer, String> names = new HashMap<>();
        for (HouseholdService.Member m : members) {
            names.put(m.getUser().getId(), m.getUser().getDisplayName());
        }
        boolean admin = Session.getInstance().isAdmin();
        for (int i = 0; i < bills.size(); i++) {
            RecurringBill b = bills.get(i);
            JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            title.setOpaque(false);
            title.add(label(b.getTitle(), Font.BOLD, 13, b.isActive() ? Theme.TEXT : Theme.MUTED_TEXT));
            if (!b.isActive()) {
                title.add(Box.createHorizontalStrut(8));
                title.add(new StatusBadge("Paused", StatusBadge.Kind.NEUTRAL));
            }
            int split = b.getMemberIds().size();
            String payer = nameOf(names, b.getPaidBy());
            String sub = payer + (payer.equals("You") ? " pay" : " pays") + DOT + "every " + ordinal(b.getDueDay());
            JPanel text = vertical();
            text.add(left(title));
            text.add(Box.createVerticalStrut(2));
            text.add(left(label(sub, Font.PLAIN, 11, Theme.MUTED_TEXT)));

            JPanel amount = vertical();
            JLabel money = label(MoneyUtil.format(b.getAmount()), Font.BOLD, 13, b.isActive() ? Theme.TEXT : Theme.MUTED_TEXT);
            JLabel ways = label(split == 1 ? "1 person" : "split " + split + " ways", Font.PLAIN, 11, Theme.MUTED_TEXT);
            money.setAlignmentX(Component.RIGHT_ALIGNMENT);
            ways.setAlignmentX(Component.RIGHT_ALIGNMENT);
            amount.add(money);
            amount.add(Box.createVerticalStrut(2));
            amount.add(ways);
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            right.setOpaque(false);
            right.add(amount);
            MoreButton more = null;
            if (admin) {
                more = new MoreButton();
                if (b.isActive()) {
                    more.item("Pause", false, () -> pauseBill(b, true));
                } else {
                    more.item("Resume", false, () -> pauseBill(b, false));
                }
                if (b.getLastGenerated() == null) {
                    more.item("Delete", true, () -> deleteBill(b));
                }
            }
            card.add(left(row(null, text, rightWith(right, more), i < bills.size() - 1)));
        }
        card.add(Box.createVerticalStrut(4));
        return card;
    }

    private JComponent buildLeave(List<HouseholdService.Member> members) {
        RoundedButton leave = RoundedButton.outline("Leave household");
        leave.setOutline(new Color(224, 96, 90, 170));
        leave.setForeground(Theme.DANGER);
        leave.setArc(RoundedButton.PILL);
        leave.setFont(Theme.font(Font.BOLD, 13));
        leave.addActionListener(e -> leave(members));
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(leave, BorderLayout.CENTER);
        holder.setPreferredSize(new Dimension(10, 40));
        return fitHeight(holder);
    }

    // =====================================================================
    //  Actions
    // =====================================================================

    private void rename() {
        FormDialog d = new FormDialog("Rename household", null);
        StyledTextField name = d.addField("Household name", new StyledTextField("e.g. Casa Mabini"));
        name.setText(Session.getInstance().getCurrentHousehold().getName());
        d.onSave("Save", () -> householdService.rename(name.getText()), () -> done("Household renamed."));
        d.showOver(this);
    }

    private void newCode() {
        new ConfirmDialog("Make a new invite code?", "The old code stops working right away.")
                .buttons("Cancel", "New code", false, () -> run(() -> {
                    String code = householdService.regenerateInviteCode();
                    done("New invite code: " + code + ".");
                }))
                .showOver(this);
    }

    private void changeRole(HouseholdService.Member m, Role role) {
        String name = m.getUser().getDisplayName();
        boolean me = m.getUser().getId() == Session.getInstance().getCurrentUser().getId();
        String title = role == Role.ADMIN ? "Make " + name + " an Admin?"
                : me ? "Step down as Admin?" : "Remove " + name + "'s Admin role?";
        String line = role == Role.ADMIN ? "Admins can rename the household, remove people and manage bills."
                : me ? "You'll stay in the household as a member." : name + " stays in the household as a member.";
        new ConfirmDialog(title, line)
                .avatar(m.getUser())
                .buttons("Cancel", role == Role.ADMIN ? "Make admin" : me ? "Step down" : "Remove admin", false,
                        () -> run(() -> {
                            householdService.setRole(m.getUser().getId(), role);
                            done(role == Role.ADMIN ? name + " is now an Admin."
                                    : me ? "You're a member now." : name + " is a member now.");
                        }))
                .showOver(this);
    }

    /** Removing someone: a warning with what they owe (if anything), then a last "are you sure". */
    private void remove(HouseholdService.Member m) {
        User user = m.getUser();
        String name = user.getDisplayName();
        List<SettlementService.Person> debts;
        try {
            debts = settlementService.getDebtsOf(user.getId());
        } catch (SettlrException e) {
            showMessage(e.getMessage(), true);
            return;
        }
        if (debts.isEmpty()) {
            new ConfirmDialog("Remove " + name + "?", null)
                    .avatar(user)
                    .buttons("Cancel", "Remove", true, () -> doRemove(user))
                    .showOver(this);
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        String[][] rows = new String[debts.size()][];
        for (int i = 0; i < debts.size(); i++) {
            BigDecimal owed = debts.get(i).getNet().abs();
            total = total.add(owed);
            rows[i] = new String[] {creditorName(debts.get(i).getUser()), MoneyUtil.format(owed)};
        }
        BigDecimal owes = total;
        new ConfirmDialog(name + " still owes money", "Removing " + name + " now leaves these unpaid.")
                .avatar(user)
                .table(rows, name + " owes", MoneyUtil.format(owes), Theme.DANGER)
                .buttons("Cancel", "Remove anyway", true, () ->
                        new ConfirmDialog("Remove " + name + " for good?",
                                "This can't be undone. " + name + "'s " + MoneyUtil.format(owes)
                                        + " stays unpaid.")
                                .avatar(user)
                                .buttons("Go back", "Yes, remove " + name, true, () -> doRemove(user))
                                .showOver(this))
                .showOver(this);
    }

    private void doRemove(User user) {
        run(() -> {
            String code = householdService.removeMember(user.getId());
            done("Removed " + user.getDisplayName() + ". New invite code: " + code + ".");
        });
    }

    /** Leaving: blocked while you owe money or are the only Admin; otherwise one confirmation. */
    private void leave(List<HouseholdService.Member> members) {
        User me = Session.getInstance().getCurrentUser();
        int admins = 0;
        for (HouseholdService.Member m : members) {
            if (m.isAdmin()) {
                admins++;
            }
        }
        if (Session.getInstance().isAdmin() && admins <= 1 && members.size() > 1) {
            new ConfirmDialog("Make someone else Admin first",
                    "You're the only Admin. Open a member's menu and pick Make admin, then you can leave.")
                    .okOnly("Close")
                    .showOver(this);
            return;
        }
        List<SettlementService.Person> debts;
        try {
            debts = settlementService.getDebtsOf(me.getId());
        } catch (SettlrException e) {
            showMessage(e.getMessage(), true);
            return;
        }
        if (!debts.isEmpty()) {
            BigDecimal total = BigDecimal.ZERO;
            String[][] rows = new String[debts.size()][];
            for (int i = 0; i < debts.size(); i++) {
                BigDecimal owed = debts.get(i).getNet().abs();
                total = total.add(owed);
                rows[i] = new String[] {debts.get(i).getUser().getDisplayName(), MoneyUtil.format(owed)};
            }
            new ConfirmDialog("You can't leave yet", "Settle up what you owe first, so nobody is left paying for you.")
                    .table(rows, "You owe", MoneyUtil.format(total), Theme.DANGER)
                    .buttons("Close", "Go to Expenses", false, () -> Navigator.getInstance().show(Navigator.EXPENSES))
                    .showOver(this);
            return;
        }
        String house = Session.getInstance().getCurrentHousehold().getName();
        new ConfirmDialog("Leave " + house + "?", "You'll need an invite code to come back.")
                .buttons("Cancel", "Leave", true, () -> run(() -> {
                    householdService.leave();
                    Navigator.getInstance().show(Navigator.HOUSEHOLD_SETUP);
                }))
                .showOver(this);
    }

    private void pauseBill(RecurringBill b, boolean pause) {
        run(() -> {
            householdService.setBillPaused(b.getId(), pause);
            done(pause ? b.getTitle() + " is paused. It won't be added to Expenses until it's resumed."
                    : b.getTitle() + " is running again.");
        });
    }

    private void deleteBill(RecurringBill b) {
        new ConfirmDialog("Delete " + b.getTitle() + "?", "It won't be added to Expenses anymore.")
                .buttons("Cancel", "Delete", true, () -> run(() -> {
                    householdService.deleteBill(b.getId());
                    done("Deleted " + b.getTitle() + ".");
                }))
                .showOver(this);
    }

    private interface Action {
        void run() throws SettlrException;
    }

    private void run(Action action) {
        try {
            action.run();
        } catch (SettlrException e) {
            rebuild();
            showMessage(e.getMessage(), true);
        }
    }

    private void done(String message) {
        rebuild();
        showMessage(message, false);
    }

    // =====================================================================
    //  Small helpers
    // =====================================================================

    private String nameOf(Map<Integer, String> names, int userId) {
        if (names.containsKey(userId)) {
            return userId == Session.getInstance().getCurrentUser().getId() ? "You" : names.get(userId);
        }
        try {
            User u = householdService.findUser(userId);
            return u == null ? "A former member" : u.getDisplayName();
        } catch (SettlrException e) {
            return "A former member";
        }
    }

    private static String creditorName(User u) {
        return u.getId() == Session.getInstance().getCurrentUser().getId() ? "You" : u.getDisplayName();
    }

    /** 1st, 2nd, 3rd, 4th ... 21st, 22nd, 23rd ... */
    private static String ordinal(int day) {
        if (day >= 11 && day <= 13) {
            return day + "th";
        }
        switch (day % 10) {
            case 1:  return day + "st";
            case 2:  return day + "nd";
            case 3:  return day + "rd";
            default: return day + "th";
        }
    }

    /** A list row: (avatar) | text | right side, with a line under it. */
    private static JPanel row(JComponent avatar, JComponent text, JComponent right, boolean line) {
        JPanel p = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                if (line) {
                    g.setColor(DIVIDER);
                    g.fillRect(0, getHeight() - 1, getWidth(), 1);
                }
            }
        };
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(9, 0, 9, 0));
        if (avatar != null) {
            p.add(centered(avatar), BorderLayout.WEST);
        }
        JPanel textHolder = new JPanel(new GridBagLayout());
        textHolder.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.weightx = 1;
        gc.anchor = GridBagConstraints.WEST;
        textHolder.add(text, gc);
        p.add(textHolder, BorderLayout.CENTER);
        if (right != null) {
            p.add(centered(right), BorderLayout.EAST);
        }
        return fitHeight(p);
    }

    private static JComponent rightWith(JComponent right, MoreButton more) {
        if (more != null) {
            right.add(more);
        }
        return right;
    }

    private static GlassCard card() {
        GlassCard card = new GlassCard(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(4, 20, 8, 20));
        return card;
    }

    private static JComponent divider() {
        JPanel line = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(DIVIDER);
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        line.setOpaque(false);
        line.setPreferredSize(new Dimension(10, 1));
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return line;
    }

    private static RoundedButton smallButton(String text, Runnable action) {
        RoundedButton b = RoundedButton.outline(text);
        b.setArc(RoundedButton.PILL);
        b.setFont(Theme.font(Font.BOLD, 12));
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setPreferredSize(new Dimension(b.getFontMetrics(b.getFont()).stringWidth(text) + 28, 28));
        if (action != null) {
            b.addActionListener(e -> action.run());
        }
        return b;
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

    /** The round "\u00B7\u00B7\u00B7" button that opens a small dark menu. */
    private static final class MoreButton extends JComponent {
        private static final Color MENU_BG = new Color(0x1E2A2A);
        private static final Color MENU_HOVER = new Color(255, 255, 255, 22);
        private final JPopupMenu menu = new JPopupMenu();
        private boolean hover;

        MoreButton() {
            Dimension d = new Dimension(30, 30);
            setPreferredSize(d);
            setMinimumSize(d);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("More");
            menu.setBackground(MENU_BG);
            menu.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(255, 255, 255, 45)), new EmptyBorder(4, 0, 4, 0)));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    menu.show(MoreButton.this, getWidth() - menu.getPreferredSize().width, getHeight() + 4);
                }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        void item(String text, boolean danger, Runnable action) {
            MenuItem item = new MenuItem(text, danger);
            item.addActionListener(e -> {
                menu.setVisible(false); // close first, so nothing is left on screen behind a popup
                action.run();
            });
            menu.add(item);
        }

        /** A menu line that draws itself (the look-and-feel's menu items ignore the dark colors). */
        private static final class MenuItem extends JMenuItem {
            private final Color color;

            MenuItem(String text, boolean danger) {
                super(text);
                color = danger ? Theme.DANGER : Theme.TEXT;
                setFont(Theme.font(Font.PLAIN, 13));
                setOpaque(false);
                setBorderPainted(false);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(getFontMetrics(getFont()).stringWidth(getText()) + 36, 32);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(MENU_BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                if (getModel().isArmed() || getModel().isRollover()) {
                    g2.setColor(MENU_HOVER);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                g2.setFont(getFont());
                g2.setColor(color);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), 16, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (hover || menu.isVisible()) {
                g2.setColor(new Color(255, 255, 255, 25));
                g2.fill(new Ellipse2D.Float(0, 0, getWidth(), getHeight()));
            }
            g2.setColor(new Color(0xC9D0CC));
            int cy = getHeight() / 2;
            for (int i = -1; i <= 1; i++) {
                g2.fill(new Ellipse2D.Float(getWidth() / 2f + i * 6 - 1.6f, cy - 1.6f, 3.2f, 3.2f));
            }
            g2.dispose();
        }
    }
}
