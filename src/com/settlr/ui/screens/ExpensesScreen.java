package com.settlr.ui.screens;

import com.settlr.exception.SettlrException;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseCategory;
import com.settlr.model.Household;
import com.settlr.service.ExpenseService;
import com.settlr.service.HouseholdService;
import com.settlr.service.PaymentInbox;
import com.settlr.service.PendingPayment;
import com.settlr.service.SettlementService;
import com.settlr.ui.Background;
import com.settlr.ui.BaseScreen;
import com.settlr.ui.components.AttentionCard;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.SlimScrollBarUI;
import com.settlr.ui.components.StatusBadge;
import com.settlr.ui.components.StyledComboBox;
import com.settlr.ui.theme.Theme;
import com.settlr.util.MoneyUtil;
import com.settlr.util.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.settlr.ui.components.Ui.*;

/**
 * Expenses page:
 *   1. Two cards on top: "You owe" (with Settle up for each person) and "You're owed".
 *      The amounts are net: what you owe someone minus what they owe you.
 *   2. A confirm bar, only when someone says they paid you.
 *   3. Two lists: Unpaid (someone still owes on it) and Settled (everyone paid).
 *      Each unpaid expense shows who still owes, e.g. "Dani \u00B7 Unpaid".
 *
 * Settling up pays the net amount (SettlementService). Older single-share payments can still be
 * confirmed here too: PaymentInbox gives both kinds as one list of PendingPayment (Adapter).
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

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final String DOT = " \u00B7 ";

    private final SettlementService settlementService = new SettlementService();
    private final ExpenseService expenseService = new ExpenseService();
    private final HouseholdService householdService = new HouseholdService();
    private final PaymentInbox paymentInbox = new PaymentInbox();

    private final Background background = new Background(); // loads off the Swing thread

    /** Everything the page shows, loaded together in the background. Each part can fail on its own. */
    private static final class ExpensesData {
        final Background.Result<List<SettlementService.Person>> people;
        final Background.Result<List<PendingPayment>> waiting;
        final Background.Result<ExpenseList> list;

        ExpensesData(ExpensesScreen s, int me) {
            people = Background.Result.of(s.settlementService::getPeople);
            waiting = Background.Result.of(s.paymentInbox::getWaitingForMe);
            list = Background.Result.of(() -> ExpenseList.load(me, s.expenseService, s.settlementService,
                    s.householdService));
        }
    }

    /** The expenses last loaded; the Unpaid sort and filter work on these without reloading. */
    private ExpenseList list;
    /** Expenses whose "+2 more" was clicked, so they stay open after a reload. */
    private final Set<Integer> expandedExpenses = new HashSet<>();

    private final JLabel crumb = label(" ", Font.PLAIN, 12, Theme.MUTED_TEXT);
    private final GlassCard oweCard = new GlassCard(null);
    private final GlassCard owedCard = new GlassCard(null);
    private final JPanel attentionCard = new AttentionCard(14);
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
        page.setBorder(new EmptyBorder(0, 40, 24, 40)); // the top bar above already leaves room
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
                g.setColor(ListRow.DIVIDER);
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
        if (isNewUser()) { // don't show the last user's money while this one's loads
            list = null;
            oweCard.removeAll();
            owedCard.removeAll();
            unpaidRows.removeAll();
            settledRows.removeAll();
            attentionCard.setVisible(false);
            repaint();
        }
        reload();
    }

    /**
     * Loads everything in the background, then fills the page. The message line isn't cleared,
     * so a result message ("Sent to Ben...") survives the reload.
     */
    private void reload() {
        int me = Session.getInstance().getCurrentUser().getId();
        background.load(() -> new ExpensesData(this, me), data -> {
            loadCards(data.people);
            loadAttention(data.waiting);
            loadLists(data.list);
            revalidate();
            repaint();
        }, e -> showMessage(e.getMessage(), true));
    }

    private void loadCards(Background.Result<List<SettlementService.Person>> people) {
        oweCard.removeAll();
        owedCard.removeAll();
        List<SettlementService.Person> owe = new ArrayList<>();
        List<SettlementService.Person> owed = new ArrayList<>();
        try {
            for (SettlementService.Person p : people.get()) {
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
                top.add(ListRow.centeredVertically(action), BorderLayout.EAST);
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
                card.add(left(new PersonAmountRow(p, iOwe, actionFor(p, iOwe))));
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

    private void loadAttention(Background.Result<List<PendingPayment>> loaded) {
        attentionCard.removeAll();
        int count = 0;
        try {
            List<PendingPayment> waiting = loaded.get();
            for (PendingPayment p : waiting) {
                attentionCard.add(left(paymentRow(p, ++count < waiting.size())));
            }
        } catch (SettlrException e) {
            showMessage(e.getMessage(), true);
        }
        attentionCard.setVisible(count > 0);
    }

    /** Confirm opens ConfirmPaymentDialog with the breakdown; Not received asks first with a ConfirmDialog. */
    private PaymentRequestRow paymentRow(PendingPayment p, boolean divider) {
        return new PaymentRequestRow(p, divider,
                () -> ConfirmPaymentDialog.open(this, p, null, () -> runThen(p::confirm, p.getConfirmedMessage())),
                () -> new ConfirmDialog("Didn't get it?", p.getRejectExplanation())
                        .avatar(p.getFromName())
                        .buttons("Cancel", "Not received", true, () -> runThen(p::reject, p.getRejectedMessage()))
                        .showOver(this));
    }

    private void loadLists(Background.Result<ExpenseList> loaded) {
        unpaidRows.removeAll();
        settledRows.removeAll();
        try {
            list = loaded.get();
        } catch (SettlrException e) {
            list = null;
            unpaidRows.add(left(muted("Expenses could not be loaded.")));
            showMessage(e.getMessage(), true);
            return;
        }

        unpaid = new ArrayList<>();
        List<Expense> settled = new ArrayList<>();
        for (Expense e : list.getExpenses()) {
            (list.isFullyPaid(e) ? settled : unpaid).add(e);
        }
        noExpensesAtAll = list.getExpenses().isEmpty();
        showUnpaid();
        if (settled.isEmpty()) {
            settledRows.add(left(muted("Nothing fully paid yet.")));
        }
        for (int i = 0; i < settled.size(); i++) {
            settledRows.add(left(new ExpenseRow(settled.get(i), list, expandedExpenses, i < settled.size() - 1, true)));
        }
    }

    /** Fills the Unpaid list in the order picked in its dropdown. */
    private void showUnpaid() {
        unpaidRows.removeAll();
        if (list == null) {
            return; // not loaded (yet)
        }
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
                BigDecimal x = list.myShare(a), y = list.myShare(b);
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
            unpaidRows.add(left(new ExpenseRow(shown.get(i), list, expandedExpenses, i < shown.size() - 1, false)));
        }
        unpaidRows.revalidate();
        unpaidRows.repaint();
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

    /** Runs the action, reloads and shows the result (or the error). */
    private void runThen(Action action, String successMessage) {
        String error = null;
        try {
            action.run();
        } catch (SettlrException ex) {
            error = ex.getMessage();
        }
        reload();
        showMessage(error != null ? error : successMessage, error != null);
    }

    // =====================================================================
    //  Small helpers
    // =====================================================================

    private static JLabel muted(String text) {
        JLabel l = label(text, Font.PLAIN, 13, Theme.MUTED_TEXT);
        l.setBorder(new EmptyBorder(14, 0, 14, 0));
        return l;
    }

    /** P1,500 instead of P1,500.00 when there are no centavos (for short detail lines). */
    static String compact(BigDecimal amount) {
        String full = MoneyUtil.format(amount);
        return full.endsWith(".00") ? full.substring(0, full.length() - 3) : full;
    }

    static String categoryName(ExpenseCategory c) {
        switch (c) {
            case RENT:        return "Rent";
            case ELECTRICITY: return "Electricity";
            case WATER:       return "Water";
            case WIFI:        return "WiFi";
            case GROCERIES:   return "Groceries";
            default:          return "Other";
        }
    }
}
