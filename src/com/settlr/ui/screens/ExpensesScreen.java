package com.settlr.ui.screens;

import com.settlr.dao.MembershipDao;
import com.settlr.dao.UserDao;
import com.settlr.exception.SettlrException;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseCategory;
import com.settlr.model.ExpenseShare;
import com.settlr.model.Household;
import com.settlr.model.User;
import com.settlr.service.BalanceService;
import com.settlr.service.ExpenseService;
import com.settlr.service.PaymentService;
import com.settlr.service.SettlementService;
import com.settlr.ui.BaseScreen;
import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.SlimScrollBarUI;
import com.settlr.ui.components.StatusBadge;
import com.settlr.ui.components.StyledComboBox;
import com.settlr.ui.theme.Theme;
import com.settlr.util.DateTimeUtil;
import com.settlr.util.MoneyUtil;
import com.settlr.util.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Expenses page:
 *   1. Two cards on top: "You owe" (with Settle up for each person) and "You're owed".
 *      The amounts are net: what you owe someone minus what they owe you.
 *   2. A confirm bar, only when someone says they paid you.
 *   3. Two lists: Unpaid (someone still owes on it) and Settled (everyone paid).
 *      Each unpaid expense shows who still owes, e.g. "Dani \u00B7 Unpaid".
 *
 * Settling up pays the net amount (SettlementService). Keith's BalanceService and
 * PaymentService still handle older single-share payments, so those can still be confirmed here.
 */
public class ExpensesScreen extends BaseScreen {

    /** One service call that can fail with a readable message. */
    private interface Action {
        void run() throws SettlrException;
    }

    /** How the Unpaid list is ordered. */
    private enum Sort {
        NEWEST("Newest first"), OLDEST("Oldest first"), TYPE("By type"), HIGHEST("Highest amount"), LOWEST("Lowest amount");

        final String label;

        Sort(String label) {
            this.label = label;
        }
    }

    /** The second dropdown under "By type": every type, or just one. */
    private enum TypeChoice {
        ALL(null), RENT(ExpenseCategory.RENT), ELECTRICITY(ExpenseCategory.ELECTRICITY),
        WATER(ExpenseCategory.WATER), WIFI(ExpenseCategory.WIFI), GROCERIES(ExpenseCategory.GROCERIES),
        OTHER(ExpenseCategory.OTHER);

        final ExpenseCategory category;

        TypeChoice(ExpenseCategory category) {
            this.category = category;
        }

        String label() {
            return category == null ? "All types" : categoryName(category);
        }
    }

    /** How far one person is with one expense. */
    private enum ShareStatus { UNPAID, SENT, PAID }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final String DOT = " \u00B7 ";
    private static final Color DIVIDER = new Color(232, 217, 205, 55);
    private static final int MAX_CHIPS = 3;

    private final SettlementService settlementService = new SettlementService();
    private final BalanceService balanceService = new BalanceService();
    private final PaymentService paymentService = new PaymentService();
    private final ExpenseService expenseService = new ExpenseService();
    private final MembershipDao membershipDao = new MembershipDao();
    private final UserDao userDao = new UserDao();

    private Map<Integer, List<ExpenseShare>> sharesByExpense = new HashMap<>();
    private Set<Integer> pendingShareIds = java.util.Collections.emptySet();
    private final Map<Integer, String> names = new HashMap<>();
    /** Expenses whose "+2 more" was clicked, so they stay open after a reload. */
    private final Set<Integer> expandedExpenses = new HashSet<>();

    private final JLabel crumb = label(" ", Font.PLAIN, 12, Theme.MUTED_TEXT);
    private final JLabel messageLabel = new JLabel(" ");
    private final GlassCard oweCard = new GlassCard(null);
    private final GlassCard owedCard = new GlassCard(null);
    private final JPanel attentionCard = new AttentionCard();
    private final JPanel unpaidRows = vertical();
    private final StyledComboBox<Sort> unpaidSort = new StyledComboBox<>(Arrays.asList(Sort.values()), s -> s.label);
    private final StyledComboBox<TypeChoice> unpaidType =
            new StyledComboBox<>(Arrays.asList(TypeChoice.values()), TypeChoice::label);
    private List<Expense> unpaid = new ArrayList<>();
    private boolean noExpensesAtAll;
    private final JPanel settledRows = vertical();

    public ExpensesScreen() {
        setBackground(Theme.BACKGROUND);
        setLayout(new BorderLayout());

        JPanel page = vertical();
        page.setBorder(new EmptyBorder(22, 40, 24, 40));
        page.add(left(buildHeader()));
        page.add(left(messageLabel));
        page.add(left(buildCards()));
        page.add(left(attentionCard));
        page.add(left(buildLists()));

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

    // =====================================================================
    //  Layout
    // =====================================================================

    private JPanel buildHeader() {
        JPanel titles = vertical();
        titles.add(left(crumb));
        titles.add(Box.createVerticalStrut(2));
        titles.add(left(label("Expenses", Font.BOLD, 30, Theme.PRIMARY)));

        RoundedButton add = RoundedButton.mint("+  Add expense");
        add.setArc(RoundedButton.PILL);
        add.setFont(Theme.font(Font.BOLD, 13));
        add.setPreferredSize(new Dimension(150, 38));
        add.addActionListener(e -> ExpenseFormDialog.open(this, () -> {
            reload();
            showMessage("Expense added.", false);
        }));
        JPanel addHolder = new JPanel(new GridBagLayout());
        addHolder.setOpaque(false);
        addHolder.add(add);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(titles, BorderLayout.WEST);
        header.add(addHolder, BorderLayout.EAST);

        messageLabel.setFont(Theme.font(Font.PLAIN, 12));
        messageLabel.setBorder(new EmptyBorder(6, 2, 6, 0));
        return header;
    }

    private JPanel buildCards() {
        for (GlassCard c : new GlassCard[] {oweCard, owedCard}) {
            c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
            c.setBorder(new EmptyBorder(14, 20, 12, 20));
        }
        JPanel cards = new JPanel(new GridLayout(1, 2, 16, 0)); // same width, same height
        cards.setOpaque(false);
        cards.add(oweCard);
        cards.add(owedCard);
        return cards;
    }

    private JPanel buildLists() {
        JPanel columns = new JPanel(new GridBagLayout());
        columns.setOpaque(false);
        columns.setBorder(new EmptyBorder(16, 0, 0, 0));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.NORTH;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.weightx = 1.3;
        c.insets = new Insets(0, 0, 0, 16);
        unpaidSort.setPreferredSize(new Dimension(136, 28));
        unpaidSort.setFont(Theme.font(Font.BOLD, 11));
        unpaidSort.addActionListener(e -> {
            unpaidType.setVisible(unpaidSort.getSelected() == Sort.TYPE); // the type picker only belongs to "By type"
            showUnpaid();
        });
        unpaidType.setPreferredSize(new Dimension(116, 28));
        unpaidType.setFont(Theme.font(Font.BOLD, 11));
        unpaidType.setVisible(false);
        unpaidType.addActionListener(e -> showUnpaid());
        JPanel sortHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)); // keeps the dropdowns their own height
        sortHolder.setOpaque(false);
        sortHolder.add(unpaidSort);
        sortHolder.add(unpaidType); // reads left to right: "By type" then "Rent"
        columns.add(narrow(listCard("Unpaid", sortHolder, unpaidRows)), c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        columns.add(narrow(listCard("Settled", label("Everyone paid", Font.PLAIN, 11, Theme.MUTED_TEXT),
                settledRows)), c);
        return columns;
    }

    private static GlassCard listCard(String title, JComponent right, JPanel rows) {
        JPanel head = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(DIVIDER);
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        };
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(12, 0, 8, 0));
        head.add(label(title, Font.BOLD, 16, Theme.TEXT), BorderLayout.WEST);
        head.add(right, BorderLayout.EAST);

        GlassCard card = new GlassCard(new BorderLayout());
        card.setBorder(new EmptyBorder(0, 18, 4, 18));
        card.add(head, BorderLayout.NORTH);
        card.add(rows, BorderLayout.CENTER);
        return card;
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

    // =====================================================================
    //  Loading
    // =====================================================================

    @Override
    public void onShow() {
        Household h = Session.getInstance().getCurrentHousehold();
        crumb.setText((h == null ? "" : h.getName() + DOT) + "Expenses");
        showMessage(" ", false);
        reload();
    }

    /** Reloads everything without clearing the message line, so a result message survives. */
    private void reload() {
        loadCards();
        loadAttention();
        loadLists();
        revalidate();
        repaint();
    }

    private void loadCards() {
        oweCard.removeAll();
        owedCard.removeAll();
        List<SettlementService.Person> owe = new ArrayList<>();
        List<SettlementService.Person> owed = new ArrayList<>();
        try {
            for (SettlementService.Person p : settlementService.getPeople()) {
                int sign = p.getNet().signum();
                if (sign > 0) {
                    owed.add(p);
                } else if (sign < 0 || p.canSettleUp()) { // even but still open: settle up for P0
                    owe.add(p);
                }
            }
        } catch (SettlrException e) {
            oweCard.add(left(label("Balances could not be loaded.", Font.PLAIN, 13, Theme.MUTED_TEXT)));
            showMessage(e.getMessage(), true);
            return;
        }
        fillCard(oweCard, owe, true);
        fillCard(owedCard, owed, false);
    }

    /**
     * One person: "You owe Ana / P1,048.20 [Settle up]".
     * More people: "You owe 2 people / P1,518.40", then one row per person with their own button.
     */
    private void fillCard(GlassCard card, List<SettlementService.Person> people, boolean iOwe) {
        BigDecimal total = BigDecimal.ZERO;
        for (SettlementService.Person p : people) {
            total = total.add(p.getNet().abs());
        }
        Color color = total.signum() == 0 ? Theme.MUTED_TEXT : (iOwe ? Theme.DANGER : Theme.MINT);
        JLabel big = label(MoneyUtil.format(total), Font.BOLD, 26, color);

        int owing = 0; // people with a real amount; someone "even" can still be in the list
        for (SettlementService.Person p : people) {
            if (p.getNet().signum() != 0) {
                owing++;
            }
        }
        String caption;
        if (people.isEmpty()) {
            caption = iOwe ? "You owe" : "You're owed";
        } else if (people.size() == 1) {
            String name = people.get(0).getUser().getDisplayName();
            caption = owing == 0 ? "You're even with " + name : iOwe ? "You owe " + name : name + " owes you";
        } else {
            String count = owing + (owing == 1 ? " person" : " people");
            caption = iOwe ? "You owe " + count : "You're owed by " + count;
        }
        JPanel text = vertical();
        text.add(left(label(caption, Font.PLAIN, 12, Theme.MUTED_TEXT)));
        text.add(Box.createVerticalStrut(2));
        text.add(left(big));

        JPanel top = new JPanel(new BorderLayout(10, 0));
        top.setOpaque(false);
        top.add(text, BorderLayout.CENTER);
        if (people.size() == 1) {
            JComponent action = actionFor(people.get(0), iOwe);
            if (action != null) {
                top.add(centeredVertically(action), BorderLayout.EAST);
            }
        }
        card.add(left(fitHeight(top)));

        if (people.isEmpty()) {
            card.add(Box.createVerticalStrut(4));
            card.add(left(label(iOwe ? "You don't owe anyone. Nice!" : "Nobody owes you right now.",
                    Font.PLAIN, 12, Theme.MUTED_TEXT)));
        } else if (people.size() > 1) {
            card.add(Box.createVerticalStrut(8));
            for (SettlementService.Person p : people) {
                card.add(left(new CardPersonRow(p, iOwe)));
            }
        }
        card.add(Box.createVerticalGlue());
    }

    /** Settle up, or a small badge when something is already waiting. Null when there's nothing to do. */
    private JComponent actionFor(SettlementService.Person p, boolean iOwe) {
        String name = p.getUser().getDisplayName();
        if (p.getPendingToMe() != null || p.getReceivedSeparately().signum() > 0) {
            return new StatusBadge("Confirm below", StatusBadge.Kind.PENDING);
        }
        if (p.getPendingFromMe() != null || p.getSentSeparately().signum() > 0) {
            return new StatusBadge("Sent" + DOT + "waiting for " + name, StatusBadge.Kind.PENDING);
        }
        if (iOwe && p.canSettleUp()) {
            RoundedButton settle = new RoundedButton("Settle up");
            settle.setArc(RoundedButton.PILL);
            settle.setFont(Theme.font(Font.BOLD, 12));
            settle.setMargin(new Insets(0, 0, 0, 0));
            settle.setPreferredSize(new Dimension(88, 30));
            settle.addActionListener(e -> onSettleUp(p));
            return settle;
        }
        return null;
    }

    private void loadAttention() {
        attentionCard.removeAll();
        int count = 0;
        try {
            List<SettlementService.Incoming> settleUps = settlementService.getWaitingForMe();
            List<BalanceService.PaymentRow> singles = balanceService.getIncomingPaymentRows();
            int total = settleUps.size() + singles.size();
            for (SettlementService.Incoming in : settleUps) {
                attentionCard.add(left(new SettleUpRow(in, ++count < total)));
            }
            for (BalanceService.PaymentRow row : singles) {
                attentionCard.add(left(new SinglePaymentRow(row, ++count < total)));
            }
        } catch (SettlrException e) {
            showMessage(e.getMessage(), true);
        }
        attentionCard.setVisible(count > 0);
    }

    private void loadLists() {
        unpaidRows.removeAll();
        settledRows.removeAll();
        List<Expense> expenses;
        try {
            expenses = expenseService.getExpenses(null);
            sharesByExpense = expenseService.getSharesByExpense();
            pendingShareIds = settlementService.getPendingShareIds();
            loadNames(expenses);
        } catch (SettlrException e) {
            unpaidRows.add(left(muted("Expenses could not be loaded.")));
            showMessage(e.getMessage(), true);
            return;
        }

        unpaid = new ArrayList<>();
        List<Expense> settled = new ArrayList<>();
        for (Expense e : expenses) {
            (isFullyPaid(e) ? settled : unpaid).add(e);
        }
        noExpensesAtAll = expenses.isEmpty();
        showUnpaid();
        if (settled.isEmpty()) {
            settledRows.add(left(muted("Nothing fully paid yet.")));
        }
        for (int i = 0; i < settled.size(); i++) {
            settledRows.add(left(new ExpenseRow(settled.get(i), i < settled.size() - 1, true)));
        }
    }

    /** Fills the Unpaid list in the order picked in its dropdown. */
    private void showUnpaid() {
        unpaidRows.removeAll();
        List<Expense> shown = new ArrayList<>(unpaid); // already newest first; the sorts below keep that for ties
        Sort sort = unpaidSort.getSelected();
        ExpenseCategory onlyType = null;
        if (sort == Sort.OLDEST) {
            java.util.Collections.reverse(shown);
        } else if (sort == Sort.TYPE) {
            TypeChoice choice = unpaidType.getSelected();
            onlyType = choice == null ? null : choice.category;
            if (onlyType != null) {
                ExpenseCategory wanted = onlyType;
                shown.removeIf(e -> e.getCategory() != wanted);
            }
            shown.sort(Comparator.comparingInt(e -> e.getCategory().ordinal()));
        } else if (sort == Sort.HIGHEST || sort == Sort.LOWEST) {
            boolean highest = sort == Sort.HIGHEST;
            shown.sort((a, b) -> {
                BigDecimal x = myShare(a), y = myShare(b);
                if (x == null || y == null) { // expenses you're not part of go last either way
                    return Boolean.compare(x == null, y == null);
                }
                return highest ? y.compareTo(x) : x.compareTo(y);
            });
        }
        if (shown.isEmpty()) {
            unpaidRows.add(left(muted(noExpensesAtAll ? "No expenses yet. Add the first one!"
                    : onlyType != null ? "No unpaid " + categoryName(onlyType) + " expenses."
                    : "Nothing unpaid. Everyone's paid up!")));
        }
        for (int i = 0; i < shown.size(); i++) {
            unpaidRows.add(left(new ExpenseRow(shown.get(i), i < shown.size() - 1, false)));
        }
        unpaidRows.revalidate();
        unpaidRows.repaint();
    }

    /** Your share of an expense, or null if you're not part of it. */
    private BigDecimal myShare(Expense e) {
        int me = Session.getInstance().getCurrentUser().getId();
        for (ExpenseShare s : sharesFor(e)) {
            if (s.getUserId() == me) {
                return s.getAmountOwed();
            }
        }
        return null;
    }

    private void loadNames(List<Expense> expenses) throws SettlrException {
        names.clear();
        Household household = Session.getInstance().getCurrentHousehold();
        if (household != null) {
            for (User u : membershipDao.findActiveMembers(household.getId())) {
                names.put(u.getId(), u.getDisplayName());
            }
        }
        for (Expense e : expenses) {
            remember(e.getPaidBy());
            for (ExpenseShare s : sharesFor(e)) {
                remember(s.getUserId());
            }
        }
    }

    /** Looks up people who left the household, so their name still shows. */
    private void remember(int userId) throws SettlrException {
        if (!names.containsKey(userId)) {
            User u = userDao.findById(userId);
            names.put(userId, u == null ? "Someone" : u.getDisplayName());
        }
    }

    // =====================================================================
    //  Expense status
    // =====================================================================

    private List<ExpenseShare> sharesFor(Expense e) {
        return sharesByExpense.getOrDefault(e.getId(), new ArrayList<>());
    }

    /** Shares other people owe the payer (the payer's own share is never "owed"). */
    private List<ExpenseShare> owedShares(Expense e) {
        List<ExpenseShare> result = new ArrayList<>();
        for (ExpenseShare s : sharesFor(e)) {
            if (s.getUserId() != e.getPaidBy() && s.getAmountOwed().signum() > 0) {
                result.add(s);
            }
        }
        return result;
    }

    private boolean isFullyPaid(Expense e) {
        for (ExpenseShare s : owedShares(e)) {
            if (!s.isSettled()) {
                return false;
            }
        }
        return true;
    }

    private ShareStatus statusOf(ExpenseShare s) {
        if (s.isSettled()) {
            return ShareStatus.PAID;
        }
        return pendingShareIds.contains(s.getId()) ? ShareStatus.SENT : ShareStatus.UNPAID;
    }

    // =====================================================================
    //  Actions
    // =====================================================================

    private void onSettleUp(SettlementService.Person p) {
        String name = p.getUser().getDisplayName();
        PaymentDialog.open(this, p, () -> {
            reload();
            showMessage("Sent to " + name + ". It counts once " + name + " confirms it.", false);
        });
    }

    /** Asks first (money is involved), then runs the action, reloads and shows the result. */
    private void confirmThen(String question, String title, Action action, String successMessage) {
        int answer = JOptionPane.showConfirmDialog(this, question, title, JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        String error = null;
        try {
            action.run();
        } catch (SettlrException ex) {
            error = ex.getMessage();
        }
        reload();
        showMessage(error != null ? error : successMessage, error != null);
    }

    private void showMessage(String text, boolean isError) {
        messageLabel.setForeground(isError ? Theme.DANGER : Theme.MINT);
        messageLabel.setText(text);
    }

    // =====================================================================
    //  Small helpers
    // =====================================================================

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

    /** Stops BoxLayout from stretching c taller than it needs. */
    private static <T extends JComponent> T fitHeight(T c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    private static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        return l;
    }

    private static JLabel muted(String text) {
        JLabel l = label(text, Font.PLAIN, 13, Theme.MUTED_TEXT);
        l.setBorder(new EmptyBorder(14, 0, 14, 0));
        return l;
    }

    private String nameOf(int userId) {
        if (userId == Session.getInstance().getCurrentUser().getId()) {
            return "You";
        }
        return names.getOrDefault(userId, "Someone");
    }

    /** P1,500 instead of P1,500.00 when there are no centavos (for short detail lines). */
    private static String compact(BigDecimal amount) {
        String full = MoneyUtil.format(amount);
        return full.endsWith(".00") ? full.substring(0, full.length() - 3) : full;
    }

    private static String categoryName(ExpenseCategory c) {
        switch (c) {
            case RENT:        return "Rent";
            case ELECTRICITY: return "Electricity";
            case WATER:       return "Water";
            case WIFI:        return "WiFi";
            case GROCERIES:   return "Groceries";
            default:          return "Other";
        }
    }

    private static JPanel leftCentered(JComponent c) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.weightx = 1;
        gc.anchor = GridBagConstraints.WEST;
        p.add(c, gc);
        return p;
    }

    private static JPanel centeredVertically(JComponent c) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.add(c);
        return p;
    }

    // =====================================================================
    //  Pieces of the page
    // =====================================================================

    /** Base for every row: fixed height and an optional divider line under it. */
    private abstract static class Row extends JPanel {
        private final boolean divider;

        Row(boolean divider, int padding) {
            this.divider = divider;
            setOpaque(false);
            setLayout(new BorderLayout(12, 0));
            setBorder(new EmptyBorder(padding, 0, padding, 0));
        }

        void finish() {
            // nothing to do: getMaximumSize below keeps the row exactly as tall as its content
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); // grows when tags open
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (divider) {
                g.setColor(DIVIDER);
                g.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
        }
    }

    /** A person inside the "You owe" / "You're owed" card when there's more than one. */
    private class CardPersonRow extends JPanel {
        CardPersonRow(SettlementService.Person p, boolean iOwe) {
            super(new BorderLayout(10, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(7, 0, 7, 0));
            String name = p.getUser().getDisplayName();
            BigDecimal amount = p.getNet().abs();

            JPanel who = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            who.setOpaque(false);
            who.add(new AvatarLabel(name, 28));
            who.add(label(name, Font.BOLD, 13, Theme.TEXT));

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            right.setOpaque(false);
            right.add(label(amount.signum() == 0 ? "Even" : MoneyUtil.format(amount), Font.BOLD, 14,
                    amount.signum() == 0 ? Theme.MUTED_TEXT : (iOwe ? Theme.DANGER : Theme.MINT)));
            JComponent action = actionFor(p, iOwe);
            if (action != null) {
                right.add(action);
            }
            add(leftCentered(who), BorderLayout.CENTER);
            add(centeredVertically(right), BorderLayout.EAST);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(DIVIDER);
            g.fillRect(0, 0, getWidth(), 1); // line above each person
        }
    }

    /**
     * One expense: title, "Ana paid P1,000 \u00B7 Nov 1 \u00B7 Rent", who still owes, and your share.
     * In Settled it just says "All paid".
     */
    private class ExpenseRow extends Row {
        ExpenseRow(Expense e, boolean divider, boolean settled) {
            super(divider, 10);
            int me = Session.getInstance().getCurrentUser().getId();
            String sub = nameOf(e.getPaidBy()) + " paid " + compact(e.getAmount()) + DOT
                    + DAY.format(e.getDueDate()) + DOT + categoryName(e.getCategory())
                    + (e.getBillId() != null ? DOT + "monthly" : "");

            JPanel text = vertical();
            text.add(left(label(e.getTitle(), Font.BOLD, 14, Theme.TEXT)));
            text.add(Box.createVerticalStrut(2));
            text.add(left(label(sub, Font.PLAIN, 11, Theme.MUTED_TEXT)));
            text.add(Box.createVerticalStrut(7));
            text.add(left(chips(e, me, settled)));

            ExpenseShare mine = null;
            for (ExpenseShare s : sharesFor(e)) {
                if (s.getUserId() == me) {
                    mine = s;
                }
            }
            JLabel amount = label(mine == null ? "\u2014" : MoneyUtil.format(mine.getAmountOwed()),
                    Font.BOLD, 14, mine == null ? Theme.MUTED_TEXT : Theme.TEXT);
            JLabel note = label(mine == null ? "not in it" : "your share", Font.PLAIN, 11, Theme.MUTED_TEXT);
            amount.setAlignmentX(Component.RIGHT_ALIGNMENT);
            note.setAlignmentX(Component.RIGHT_ALIGNMENT);
            JPanel right = vertical();
            right.add(amount);
            right.add(Box.createVerticalStrut(2));
            right.add(note);

            add(text, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);
            finish();
        }

        /**
         * "You \u00B7 Unpaid", "Carlo \u00B7 Sent", "Dani \u00B7 Paid", then "+1 paid" or "+2 more".
         * Clicking "+2 more" shows everyone's tag; "Show less" folds them back.
         * Settled expenses just say "All paid", with "See who" to show the same tags.
         */
        private JPanel chips(Expense e, int me, boolean settled) {
            List<ExpenseShare> shares = owedShares(e);
            shares.sort(Comparator.<ExpenseShare>comparingInt(s -> s.getUserId() == me ? 0 : 1)
                    .thenComparing(s -> statusOf(s).ordinal())
                    .thenComparing(s -> nameOf(s.getUserId()), String.CASE_INSENSITIVE_ORDER));
            List<JComponent> badges = new ArrayList<>();
            for (ExpenseShare s : shares) {
                ShareStatus status = statusOf(s);
                String text = nameOf(s.getUserId()) + DOT
                        + (status == ShareStatus.PAID ? "Paid" : status == ShareStatus.SENT ? "Sent" : "Unpaid");
                StatusBadge.Kind kind = status == ShareStatus.PAID ? StatusBadge.Kind.OK
                        : status == ShareStatus.SENT ? StatusBadge.Kind.PENDING : StatusBadge.Kind.LIGHT;
                badges.add(new StatusBadge(text, kind));
            }
            JPanel holder = vertical();
            fillChips(holder, e.getId(), badges, shares, settled);
            return holder;
        }

        private void fillChips(JPanel holder, int expenseId, List<JComponent> badges, List<ExpenseShare> shares,
                               boolean settled) {
            holder.removeAll();
            Runnable redraw = () -> fillChips(holder, expenseId, badges, shares, settled);
            boolean open = expandedExpenses.contains(expenseId);
            if (settled && !open) {
                // Settled: just "All paid", and "See who" when there's anyone to show
                List<JComponent> parts = new ArrayList<>();
                parts.add(new StatusBadge("All paid", StatusBadge.Kind.OK));
                if (!badges.isEmpty()) {
                    parts.add(link("See who", () -> {
                        expandedExpenses.add(expenseId);
                        redraw.run();
                    }));
                }
                holder.add(left(chipRow(parts.toArray(new JComponent[0]))));
            } else if (!settled && (!open || badges.size() <= MAX_CHIPS)) {
                List<JComponent> parts = new ArrayList<>(badges.subList(0, Math.min(MAX_CHIPS, badges.size())));
                int rest = badges.size() - MAX_CHIPS;
                if (rest > 0) {
                    boolean allPaid = true;
                    for (ExpenseShare s : shares.subList(MAX_CHIPS, shares.size())) {
                        allPaid &= s.isSettled();
                    }
                    parts.add(link("+" + rest + (allPaid ? " paid" : " more"), () -> {
                        expandedExpenses.add(expenseId);
                        redraw.run();
                    }));
                }
                holder.add(left(chipRow(parts.toArray(new JComponent[0]))));
            } else {
                JComponent less = link("Show less", () -> {
                    expandedExpenses.remove(expenseId);
                    redraw.run();
                });
                for (int i = 0; i < badges.size(); i += MAX_CHIPS) {
                    List<JComponent> parts = new ArrayList<>(badges.subList(i, Math.min(i + MAX_CHIPS, badges.size())));
                    boolean last = i + MAX_CHIPS >= badges.size();
                    if (last && parts.size() < MAX_CHIPS) {
                        parts.add(less);
                    }
                    if (i > 0) {
                        holder.add(Box.createVerticalStrut(6));
                    }
                    holder.add(left(chipRow(parts.toArray(new JComponent[0]))));
                }
                if (badges.size() % MAX_CHIPS == 0) {
                    holder.add(Box.createVerticalStrut(6));
                    holder.add(left(chipRow(less)));
                }
            }
            holder.revalidate();
            holder.repaint();
        }

        /** Small beige text that acts like a link. */
        private JLabel link(String text, Runnable action) {
            JLabel l = label(text, Font.BOLD, 11, Theme.PRIMARY);
            l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            l.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { action.run(); }
                @Override public void mouseEntered(MouseEvent e) { l.setText("<html><u>" + text + "</u></html>"); }
                @Override public void mouseExited(MouseEvent e) { l.setText(text); }
            });
            return l;
        }

        private JPanel chipRow(JComponent... parts) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            p.setOpaque(false);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    p.add(Box.createHorizontalStrut(6));
                }
                p.add(parts[i]);
            }
            return p;
        }
    }

    /** "Ben says he paid you..." row with Not received / Confirm on the right. */
    private abstract class ActionRow extends Row {
        ActionRow(boolean divider) {
            super(divider, 9);
        }

        void build(String person, String headline, String sub, Runnable confirm, Runnable reject) {
            JPanel text = vertical();
            text.add(left(label(headline, Font.BOLD, 14, Theme.TEXT)));
            text.add(Box.createVerticalStrut(2));
            text.add(left(label(sub, Font.PLAIN, 11, Theme.MUTED_TEXT)));

            String rejectText = "Not received";
            JLabel rejectLink = label(rejectText, Font.BOLD, 12, Theme.PRIMARY);
            rejectLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            rejectLink.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { reject.run(); }
                @Override public void mouseEntered(MouseEvent e) { rejectLink.setText("<html><u>" + rejectText + "</u></html>"); }
                @Override public void mouseExited(MouseEvent e) { rejectLink.setText(rejectText); }
            });
            RoundedButton confirmButton = new RoundedButton("Confirm");
            confirmButton.setArc(RoundedButton.PILL);
            confirmButton.setFont(Theme.font(Font.BOLD, 12));
            confirmButton.setMargin(new Insets(0, 0, 0, 0));
            confirmButton.setPreferredSize(new Dimension(88, 30));
            confirmButton.addActionListener(e -> confirm.run());
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
            actions.setOpaque(false);
            actions.add(rejectLink);
            actions.add(confirmButton);

            add(new AvatarLabel(person, 34), BorderLayout.WEST);
            add(leftCentered(text), BorderLayout.CENTER);
            add(centeredVertically(actions), BorderLayout.EAST);
            finish();
        }
    }

    /** "Ben says he paid you P1,048.20 / GCash ref 1234 \u00B7 just now   Not received [Confirm]". */
    private class SettleUpRow extends ActionRow {
        SettleUpRow(SettlementService.Incoming in, boolean divider) {
            super(divider);
            String from = in.getFromName();
            BigDecimal amount = in.getSettlement().getAmount();
            String note = in.getSettlement().getNote();
            String headline = amount.signum() == 0
                    ? from + " says you're even"
                    : from + " says they paid you " + MoneyUtil.format(amount);
            String sub = (note == null || note.isBlank() ? "" : note + DOT)
                    + DateTimeUtil.timeAgo(in.getSettlement().getCreatedAt());
            int id = in.getSettlement().getId();
            build(from, headline, sub,
                    () -> confirmThen((amount.signum() == 0 ? "Confirm that you and " + from + " are even?"
                                    : "Did " + from + " pay you " + MoneyUtil.format(amount) + "?")
                                    + "\nThis settles everything between you two.",
                            "Confirm payment", () -> settlementService.confirm(id),
                            "Confirmed. You and " + from + " are settled up."),
                    () -> confirmThen("Mark it as not received?\nNothing will be settled, and " + from
                                    + " can send it again.", "Not received", () -> settlementService.reject(id),
                            "Marked as not received. Nothing was settled."));
        }
    }

    /** An older single-share payment waiting for confirmation (made before settle ups existed). */
    private class SinglePaymentRow extends ActionRow {
        SinglePaymentRow(BalanceService.PaymentRow row, boolean divider) {
            super(divider);
            String from = row.getFromUserName();
            String amount = MoneyUtil.format(row.getAmount());
            build(from, from + " says they paid you " + amount,
                    "for " + row.getExpenseTitle() + DOT + DateTimeUtil.timeAgo(row.getPaidAt()),
                    () -> confirmThen("Did " + from + " pay you " + amount + " for "
                                    + row.getExpenseTitle() + "?", "Confirm payment",
                            () -> paymentService.confirm(row.getPaymentId()),
                            "Confirmed. " + from + "'s payment is now settled."),
                    () -> confirmThen("Mark " + from + "'s payment of " + amount
                                    + " as not received?\nThe share will stay unpaid.", "Not received",
                            () -> paymentService.reject(row.getPaymentId()),
                            "Marked as not received. The share is still unpaid."));
        }
    }

    /** The amber-tinted bar for payments waiting for you. */
    private static class AttentionCard extends JPanel {
        AttentionCard() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(2, 18, 2, 18));
        }

        @Override
        public Insets getInsets() {
            Insets in = super.getInsets();
            return new Insets(in.top + 14, in.left, in.bottom, in.right); // gap above the bar
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape card = new RoundRectangle2D.Float(0.5f, 14.5f, getWidth() - 1f, getHeight() - 15f, 22, 22);
            g2.setColor(Theme.BANNER);
            g2.fill(card);
            g2.setColor(new Color(201, 164, 110, 120));
            g2.draw(card);
            g2.dispose();
        }
    }
}
