package com.settlr.ui.screens;

import com.settlr.dao.MembershipDao;
import com.settlr.dao.UserDao;
import com.settlr.exception.SettlrException;
import com.settlr.model.Balance;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseCategory;
import com.settlr.model.ExpenseShare;
import com.settlr.model.Household;
import com.settlr.model.User;
import com.settlr.service.BalanceService;
import com.settlr.service.ExpenseService;
import com.settlr.service.PaymentService;
import com.settlr.ui.BaseScreen;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.theme.Theme;
import com.settlr.util.DateTimeUtil;
import com.settlr.util.MoneyUtil;
import com.settlr.util.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Expenses page with two tabs (layout option C):
 *   - Expenses: category chips, the list of shared expenses with "your share", and "+ Add expenses".
 *   - Balances: payments waiting for you to confirm, and who owes whom with Settle Up.
 * The "You owe" / "Owed to you" totals sit on the tab row so they show on both tabs, and
 * the Balances tab shows a red badge with the number of payments waiting for you.
 *
 * Balance and payment logic comes from Keith's BalanceService and PaymentService;
 * this screen and PaymentDialog only show it.
 */
public class ExpensesScreen extends BaseScreen {

    /** The category chips above the list. "Bills" groups electricity, water and wifi. */
    private enum Filter {
        ALL("All"), RENT("Rent"), BILLS("Bills"), GROCERIES("Groceries"), OTHER("Other");

        final String label;

        Filter(String label) {
            this.label = label;
        }

        boolean matches(ExpenseCategory c) {
            switch (this) {
                case ALL:       return true;
                case RENT:      return c == ExpenseCategory.RENT;
                case BILLS:     return c == ExpenseCategory.ELECTRICITY || c == ExpenseCategory.WATER
                                        || c == ExpenseCategory.WIFI;
                case GROCERIES: return c == ExpenseCategory.GROCERIES;
                default:        return c == ExpenseCategory.OTHER;
            }
        }
    }

    /** One service call that can fail with a readable message. */
    private interface PaymentAction {
        void run() throws SettlrException;
    }

    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final String DOT = " \u2022 ";
    private static final Color DIVIDER = new Color(232, 217, 205, 60);
    private static final String EXPENSES_TAB = "expenses";
    private static final String BALANCES_TAB = "balances";

    private final ExpenseService expenseService = new ExpenseService();
    private final BalanceService balanceService = new BalanceService();
    private final PaymentService paymentService = new PaymentService();
    private final MembershipDao membershipDao = new MembershipDao();
    private final UserDao userDao = new UserDao();

    private List<Expense> expenses = new ArrayList<>();
    private Map<Integer, List<ExpenseShare>> sharesByExpense = new HashMap<>();
    private final Map<Integer, String> names = new HashMap<>();
    private Filter filter = Filter.ALL;

    private final JLabel subtitle = new JLabel(" ");
    private final JLabel messageLabel = new JLabel(" ");
    private final StatPill owePill = new StatPill("You owe", Theme.DANGER);
    private final StatPill owedPill = new StatPill("Owed to you", Theme.MINT);
    private final Tab expensesTab = new Tab("Expenses");
    private final Tab balancesTab = new Tab("Balances");
    private final CardLayout tabCards = new CardLayout();
    private final JPanel tabContent = new JPanel(tabCards);

    // Expenses tab
    private final JPanel expensesPage = new JPanel(new GridBagLayout());
    private final JPanel chipsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final RowList expenseRows = new RowList();
    private final JPanel addRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    private GlassCard listCard;

    // Balances tab
    private final JPanel waitingRows = verticalPanel();
    private final JPanel owesRows = verticalPanel();
    private GlassCard waitingCard;

    public ExpensesScreen() {
        setBackground(Theme.BACKGROUND);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(26, 40, 20, 40));

        add(buildTop(), BorderLayout.NORTH);
        tabContent.setOpaque(false);
        tabContent.add(buildExpensesTab(), EXPENSES_TAB);
        tabContent.add(buildBalancesTab(), BALANCES_TAB);
        add(tabContent, BorderLayout.CENTER);
        showTab(EXPENSES_TAB);
    }

    // =====================================================================
    //  Layout
    // =====================================================================

    private JPanel buildTop() {
        JLabel title = new JLabel("Expenses");
        title.setFont(Theme.font(Font.BOLD, 30));
        title.setForeground(Theme.PRIMARY);
        subtitle.setFont(Theme.font(Font.PLAIN, 12));
        subtitle.setForeground(Theme.MUTED_TEXT);
        messageLabel.setFont(Theme.font(Font.PLAIN, 12));
        messageLabel.setBorder(new EmptyBorder(2, 2, 2, 0));

        expensesTab.addMouseListener(clickTo(() -> showTab(EXPENSES_TAB)));
        balancesTab.addMouseListener(clickTo(() -> showTab(BALANCES_TAB)));
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabs.setOpaque(false);
        tabs.add(expensesTab);
        tabs.add(Box.createHorizontalStrut(22));
        tabs.add(balancesTab);

        JPanel pills = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        pills.setOpaque(false);
        pills.add(owePill);
        pills.add(owedPill);

        JPanel tabRow = new JPanel(new BorderLayout());
        tabRow.setOpaque(false);
        tabRow.add(tabs, BorderLayout.WEST);
        tabRow.add(pills, BorderLayout.EAST);

        JPanel top = verticalPanel();
        top.add(left(title));
        top.add(left(subtitle));
        top.add(left(messageLabel));
        top.add(Box.createVerticalStrut(4));
        top.add(left(tabRow));
        top.add(Box.createVerticalStrut(14));
        return top;
    }

    private static MouseAdapter clickTo(Runnable action) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
        };
    }

    private void showTab(String name) {
        tabCards.show(tabContent, name);
        expensesTab.setSelected(EXPENSES_TAB.equals(name));
        balancesTab.setSelected(BALANCES_TAB.equals(name));
    }

    private JPanel buildExpensesTab() {
        expensesPage.setOpaque(false);

        chipsRow.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (Filter f : Filter.values()) {
            Chip chip = new Chip(f.label, f == Filter.ALL);
            chip.addActionListener(e -> {
                filter = f;
                rebuildExpenseList();
            });
            group.add(chip);
            chipsRow.add(chip);
        }

        JScrollPane scroll = transparentScroll(expenseRows);
        listCard = new GlassCard(new BorderLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(1, Math.min(d.height, maxListHeight()));
            }
        };
        listCard.setBorder(new EmptyBorder(6, 18, 6, 14));
        listCard.add(scroll, BorderLayout.CENTER);

        RoundedButton add = RoundedButton.mint("+  Add expenses");
        add.setArc(RoundedButton.PILL);
        add.setFont(Theme.font(Font.BOLD, 13));
        add.setPreferredSize(new Dimension(150, 34));
        add.addActionListener(e -> ExpenseFormDialog.open(this, () -> {
            reload();
            showMessage("Expense added.", false);
        }));
        addRow.setOpaque(false);
        addRow.add(add);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;
        c.gridy = 0;
        expensesPage.add(chipsRow, c);
        c.gridy = 1;
        c.insets = new Insets(10, 0, 0, 0);
        expensesPage.add(listCard, c);
        c.gridy = 2;
        c.insets = new Insets(12, 0, 0, 0);
        expensesPage.add(addRow, c);
        c.gridy = 3;
        c.weighty = 1;
        c.insets = new Insets(0, 0, 0, 0);
        expensesPage.add(Box.createGlue(), c);

        // When the window is resized, the list card may grow or shrink
        expensesPage.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                listCard.revalidate();
            }
        });
        return expensesPage;
    }

    /** The list card grows with its rows until it would push "Add expenses" off the screen; then it scrolls. */
    private int maxListHeight() {
        int available = expensesPage.getHeight() - chipsRow.getPreferredSize().height
                - addRow.getPreferredSize().height - 10 - 12 - 4;
        return available > 120 ? available : 340;
    }

    private JComponent buildBalancesTab() {
        waitingCard = new GlassCard(new BorderLayout(0, 4));
        waitingCard.setBorder(new EmptyBorder(14, 20, 10, 20));
        waitingCard.add(label("WAITING FOR YOU", Font.BOLD, 12, Theme.AMBER), BorderLayout.NORTH);
        waitingCard.add(waitingRows, BorderLayout.CENTER);

        GlassCard owesCard = new GlassCard(new BorderLayout(0, 4));
        owesCard.setBorder(new EmptyBorder(14, 20, 10, 20));
        JPanel owesTitle = verticalPanel();
        owesTitle.add(left(label("Who owes whom", Font.BOLD, 15, Theme.TEXT)));
        owesTitle.add(left(label("Amounts are net: what you owe each other cancels out.",
                Font.PLAIN, 11, Theme.MUTED_TEXT)));
        owesCard.add(owesTitle, BorderLayout.NORTH);
        owesCard.add(owesRows, BorderLayout.CENTER);

        JPanel page = verticalPanel();
        page.add(left(waitingCard));
        page.add(Box.createVerticalStrut(14));
        page.add(left(owesCard));
        page.add(Box.createVerticalGlue());

        JPanel holder = new JPanel(new BorderLayout()); // keeps the cards at the top, full width
        holder.setOpaque(false);
        holder.add(page, BorderLayout.NORTH);
        return transparentScroll(holder);
    }

    private static JScrollPane transparentScroll(JComponent view) {
        JScrollPane scroll = new JScrollPane(view,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    // =====================================================================
    //  Loading
    // =====================================================================

    @Override
    public void onShow() {
        showMessage(" ", false);
        reload();
    }

    /** Reloads everything without clearing the message line, so a result message survives. */
    private void reload() {
        Household household = Session.getInstance().getCurrentHousehold();
        subtitle.setText("Shared bills and who owes whom in "
                + (household == null ? "your household" : household.getName()) + ".");
        try {
            expenses = expenseService.getExpenses(null);
            sharesByExpense = expenseService.getSharesByExpense();
            loadNames(household);
        } catch (SettlrException e) {
            expenses = new ArrayList<>();
            sharesByExpense = new HashMap<>();
            showMessage(e.getMessage(), true);
        }
        rebuildExpenseList();
        loadBalances();
        loadWaitingPayments();
        revalidate();
        repaint();
    }

    /** Display names for "Paid by ...", including people who have since left the household. */
    private void loadNames(Household household) throws SettlrException {
        names.clear();
        if (household != null) {
            for (User u : membershipDao.findActiveMembers(household.getId())) {
                names.put(u.getId(), u.getDisplayName());
            }
        }
        for (Expense e : expenses) {
            if (!names.containsKey(e.getPaidBy())) {
                User u = userDao.findById(e.getPaidBy());
                names.put(e.getPaidBy(), u == null ? "someone" : u.getDisplayName());
            }
        }
    }

    private void rebuildExpenseList() {
        expenseRows.removeAll();
        List<Expense> shown = new ArrayList<>();
        for (Expense e : expenses) {
            if (filter.matches(e.getCategory())) {
                shown.add(e);
            }
        }
        if (shown.isEmpty()) {
            String text = filter == Filter.ALL
                    ? "No expenses yet. Add the first one!"
                    : "No " + filter.label.toLowerCase() + " expenses yet.";
            JLabel empty = label(text, Font.PLAIN, 13, Theme.MUTED_TEXT);
            empty.setBorder(new EmptyBorder(14, 2, 14, 0));
            expenseRows.add(left(empty));
        }
        for (int i = 0; i < shown.size(); i++) {
            expenseRows.add(left(new ExpenseRow(shown.get(i), i < shown.size() - 1)));
        }
        expenseRows.revalidate();
        listCard.revalidate();
        repaint();
    }

    private void loadBalances() {
        owesRows.removeAll();
        try {
            owePill.setValue(MoneyUtil.format(balanceService.getTotalIOwe()));
            owedPill.setValue(MoneyUtil.format(balanceService.getTotalOwedToMe()));
            List<Balance> balances = balanceService.getMyBalances();
            if (balances.isEmpty()) {
                owesRows.add(left(muted("It's just you here. Share your invite code!")));
            }
            for (int i = 0; i < balances.size(); i++) {
                Balance b = balances.get(i);
                int other = b.getOtherUser().getId();
                owesRows.add(left(new BalanceRow(b, i < balances.size() - 1,
                        balanceService.getIOweThemBeforeNetting(other),
                        balanceService.getTheyOweMeBeforeNetting(other))));
            }
        } catch (SettlrException e) {
            owePill.setValue("-");
            owedPill.setValue("-");
            owesRows.add(left(muted("Balances could not be loaded.")));
            showMessage(e.getMessage(), true);
        }
    }

    private void loadWaitingPayments() {
        waitingRows.removeAll();
        int count = 0;
        try {
            List<BalanceService.PaymentRow> rows = balanceService.getIncomingPaymentRows();
            count = rows.size();
            for (int i = 0; i < rows.size(); i++) {
                waitingRows.add(left(new WaitingRow(rows.get(i), i < rows.size() - 1)));
            }
        } catch (SettlrException e) {
            showMessage(e.getMessage(), true);
        }
        waitingCard.setVisible(count > 0); // nothing to confirm = no card
        balancesTab.setBadge(count);
    }

    // =====================================================================
    //  Actions (same flow as the old Balances page)
    // =====================================================================

    private void onSettleUp(Balance b) {
        String name = b.getOtherUser().getDisplayName();
        PaymentDialog.open(this, b.getOtherUser(), () -> {
            reload();
            showMessage("Payment sent to " + name + ". It counts once " + name + " confirms it.", false);
        });
    }

    private void onConfirm(BalanceService.PaymentRow row) {
        int answer = JOptionPane.showConfirmDialog(this,
                "Confirm that you received " + MoneyUtil.format(row.getAmount())
                        + " from " + row.getFromUserName() + "?",
                "Confirm payment", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            runAndRefresh(() -> paymentService.confirm(row.getPaymentId()),
                    "Confirmed. " + row.getFromUserName() + "'s payment is now settled.");
        }
    }

    private void onReject(BalanceService.PaymentRow row) {
        int answer = JOptionPane.showConfirmDialog(this,
                "Reject " + row.getFromUserName() + "'s payment of " + MoneyUtil.format(row.getAmount())
                        + "?" + "\n" + "The share will stay unpaid.",
                "Reject payment", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            runAndRefresh(() -> paymentService.reject(row.getPaymentId()),
                    "Rejected. The share is still unpaid.");
        }
    }

    /** Runs the action, always reloads (the data may have changed even on failure), then shows the result. */
    private void runAndRefresh(PaymentAction action, String successMessage) {
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

    private static JPanel verticalPanel() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        return l;
    }

    private static JLabel muted(String text) {
        JLabel l = label(text, Font.PLAIN, 12, Theme.MUTED_TEXT);
        l.setBorder(new EmptyBorder(8, 0, 8, 0));
        return l;
    }

    private String nameOf(int userId) {
        if (userId == Session.getInstance().getCurrentUser().getId()) {
            return "you";
        }
        return names.getOrDefault(userId, "someone");
    }

    private static void paintDivider(Graphics g, JComponent c) {
        g.setColor(DIVIDER);
        g.fillRect(0, c.getHeight() - 1, c.getWidth(), 1);
    }

    // =====================================================================
    //  Pieces of the page
    // =====================================================================

    /** One expense: avatar, title, "Paid by ... | due ... | split N ways", amount and your share. */
    private class ExpenseRow extends JPanel {
        private final boolean divider;

        ExpenseRow(Expense e, boolean divider) {
            this.divider = divider;
            setOpaque(false);
            setLayout(new BorderLayout(12, 0));
            setBorder(new EmptyBorder(8, 0, 8, 0));

            List<ExpenseShare> shares = sharesByExpense.getOrDefault(e.getId(), new ArrayList<>());
            String how = e.getBillId() != null ? "monthly bill"
                    : shares.size() <= 1 ? "not split" : "split " + shares.size() + " ways";
            String meta = "Paid by " + nameOf(e.getPaidBy()) + DOT + "due " + DUE.format(e.getDueDate()) + DOT + how;

            JPanel text = verticalPanel();
            text.add(left(label(e.getTitle(), Font.BOLD, 13, Theme.TEXT)));
            text.add(left(label(meta, Font.PLAIN, 11, Theme.MUTED_TEXT)));

            JPanel money = verticalPanel();
            JLabel amount = label(MoneyUtil.format(e.getAmount()), Font.BOLD, 13, Theme.TEXT);
            JLabel note = label(myPartOf(e, shares), Font.PLAIN, 11, Theme.MUTED_TEXT);
            amount.setAlignmentX(Component.RIGHT_ALIGNMENT);
            note.setAlignmentX(Component.RIGHT_ALIGNMENT);
            money.add(amount);
            money.add(note);

            add(new Avatar(e.getTitle()), BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            add(money, BorderLayout.EAST);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        }

        private String myPartOf(Expense e, List<ExpenseShare> shares) {
            int me = Session.getInstance().getCurrentUser().getId();
            if (e.getPaidBy() == me) {
                return "you paid";
            }
            for (ExpenseShare s : shares) {
                if (s.getUserId() == me) {
                    return "your share " + MoneyUtil.format(s.getAmountOwed())
                            + (s.getSettledAt() != null ? DOT + "paid" : "");
                }
            }
            return "not part of it";
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (divider) {
                paintDivider(g, this);
            }
        }
    }

    /**
     * "You owe Ben P375.00 [Settle Up]" or "Ana owes you P2,412.25". When money goes both
     * ways, a second line shows how the net amount was worked out, so it matches Settle Up.
     */
    private class BalanceRow extends JPanel {
        private final boolean divider;

        BalanceRow(Balance b, boolean divider, BigDecimal iOweThem, BigDecimal theyOweMe) {
            this.divider = divider;
            setOpaque(false);
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(7, 0, 7, 0));

            String name = b.getOtherUser().getDisplayName();
            JLabel text;
            if (b.iOweThem()) {
                text = label("You owe " + name + " " + MoneyUtil.format(b.getAbsoluteAmount()),
                        Font.BOLD, 13, Theme.DANGER);
                RoundedButton settle = new RoundedButton("Settle Up");
                settle.setArc(RoundedButton.PILL);
                settle.setFont(Theme.font(Font.BOLD, 11));
                settle.setMargin(new Insets(0, 0, 0, 0)); // no hidden padding, so the text fits
                settle.setPreferredSize(new Dimension(82, 26));
                settle.addActionListener(e -> onSettleUp(b));
                JPanel holder = new JPanel(new GridBagLayout()); // centers the button vertically
                holder.setOpaque(false);
                holder.add(settle);
                add(holder, BorderLayout.EAST);
            } else if (b.theyOweMe()) {
                text = label(name + " owes you " + MoneyUtil.format(b.getAbsoluteAmount()),
                        Font.BOLD, 13, Theme.TEXT);
            } else {
                text = label(name + ": all settled", Font.PLAIN, 13, Theme.MUTED_TEXT);
            }
            JPanel texts = verticalPanel();
            texts.add(left(text));
            if (iOweThem.signum() > 0 && theyOweMe.signum() > 0) {
                String how = b.iOweThem()
                        ? MoneyUtil.format(iOweThem) + " you owe \u2212 " + MoneyUtil.format(theyOweMe) + " " + name + " owes you"
                        : MoneyUtil.format(theyOweMe) + " " + name + " owes you \u2212 " + MoneyUtil.format(iOweThem) + " you owe";
                texts.add(left(label(how, Font.PLAIN, 11, Theme.MUTED_TEXT)));
            }
            JPanel centered = new JPanel(new GridBagLayout()); // centers the text block vertically
            centered.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.weightx = 1;
            gc.anchor = GridBagConstraints.WEST;
            centered.add(texts, gc);

            add(new Avatar(name), BorderLayout.WEST);
            add(centered, BorderLayout.CENTER);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (divider) {
                paintDivider(g, this);
            }
        }
    }

    /** Outlined pill in the header: "You owe P375.00". */
    private static class StatPill extends JPanel {
        private final JLabel value;

        StatPill(String caption, Color valueColor) {
            setOpaque(false);
            setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
            setBorder(new EmptyBorder(6, 12, 6, 12));
            add(label(caption, Font.PLAIN, 11, Theme.MUTED_TEXT));
            value = label("-", Font.BOLD, 14, valueColor);
            add(value);
        }

        void setValue(String text) {
            value.setText(text);
            revalidate();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            Shape pill = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, h - 1f, h, h);
            g2.setColor(Theme.GLASS_FILL);
            g2.fill(pill);
            g2.setColor(Theme.GLASS_EDGE);
            g2.draw(pill);
            g2.dispose();
        }
    }

    /** Category chip: filled when selected, outlined otherwise. */
    private static class Chip extends JToggleButton {
        Chip(String text, boolean selected) {
            super(text, selected);
            setFont(Theme.font(Font.BOLD, 11));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            int w = getFontMetrics(getFont()).stringWidth(text) + 26;
            setPreferredSize(new Dimension(w, 24));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Shape pill = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, h, h);
            if (isSelected()) {
                g2.setColor(Theme.TEXT);
                g2.fill(pill);
            } else {
                g2.setColor(getModel().isRollover() ? new Color(255, 255, 255, 25) : new Color(0, 0, 0, 0));
                g2.fill(pill);
                g2.setColor(new Color(255, 255, 255, 110));
                g2.draw(pill);
            }
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(isSelected() ? Theme.ON_PRIMARY : Theme.TEXT);
            g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Grey circle with a first letter, like the Figma placeholders. */
    private static class Avatar extends JComponent {
        private static final int SIZE = 30;
        private final String letter;

        Avatar(String name) {
            this.letter = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            Dimension d = new Dimension(SIZE, SIZE);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int y = (getHeight() - SIZE) / 2;
            g2.setColor(new Color(255, 255, 255, 45));
            g2.fill(new Ellipse2D.Float(0, y, SIZE, SIZE));
            g2.setFont(Theme.font(Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.TEXT);
            g2.drawString(letter, (SIZE - fm.stringWidth(letter)) / 2, y + (SIZE - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** The expense rows; follows the scroll area's width so long titles don't make it scroll sideways. */
    private static class RowList extends JPanel implements Scrollable {
        RowList() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** One payment to confirm: "Carlo paid P2,400.00 for Rent / 3d ago | only counts once you confirm  Reject [Confirm]". */
    private class WaitingRow extends JPanel {
        private final boolean divider;

        WaitingRow(BalanceService.PaymentRow row, boolean divider) {
            this.divider = divider;
            setOpaque(false);
            setLayout(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(8, 0, 8, 0));

            JPanel text = verticalPanel();
            text.add(left(label(row.getFromUserName() + " paid " + MoneyUtil.format(row.getAmount())
                    + " for " + row.getExpenseTitle(), Font.BOLD, 13, Theme.TEXT)));
            text.add(left(label(DateTimeUtil.timeAgo(row.getPaidAt()) + DOT + "only counts once you confirm",
                    Font.PLAIN, 11, Theme.MUTED_TEXT)));

            JLabel reject = label("Reject", Font.BOLD, 12, Theme.DANGER);
            reject.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            reject.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { onReject(row); }
                @Override public void mouseEntered(MouseEvent e) { reject.setText("<html><u>Reject</u></html>"); }
                @Override public void mouseExited(MouseEvent e) { reject.setText("Reject"); }
            });
            RoundedButton confirm = new RoundedButton("Confirm");
            confirm.setArc(RoundedButton.PILL);
            confirm.setFont(Theme.font(Font.BOLD, 11));
            confirm.setMargin(new Insets(0, 0, 0, 0));
            confirm.setPreferredSize(new Dimension(80, 26));
            confirm.addActionListener(e -> onConfirm(row));

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
            actions.setOpaque(false);
            actions.add(reject);
            actions.add(confirm);
            JPanel holder = new JPanel(new GridBagLayout()); // centers the buttons vertically
            holder.setOpaque(false);
            holder.add(actions);

            add(text, BorderLayout.CENTER);
            add(holder, BorderLayout.EAST);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (divider) {
                paintDivider(g, this);
            }
        }
    }

    /** A tab title; the selected one is beige and underlined. Can show a red count badge. */
    private static class Tab extends JComponent {
        private final String text;
        private boolean selected;
        private int badge;

        Tab(String text) {
            this.text = text;
            setFont(Theme.font(Font.BOLD, 15));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            resize();
        }

        void setSelected(boolean selected) {
            this.selected = selected;
            repaint();
        }

        void setBadge(int count) {
            badge = count;
            resize();
            revalidate();
            repaint();
        }

        private void resize() {
            int w = getFontMetrics(getFont()).stringWidth(text) + (badge > 0 ? 28 : 0);
            setPreferredSize(new Dimension(w, 28));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(text);
            g2.setColor(selected ? Theme.PRIMARY : Theme.MUTED_TEXT);
            g2.drawString(text, 0, fm.getAscent() + 2);
            if (selected) {
                g2.fillRect(0, getHeight() - 2, textWidth, 2);
            }
            if (badge > 0) {
                String n = String.valueOf(badge);
                g2.setFont(Theme.font(Font.BOLD, 11));
                FontMetrics bm = g2.getFontMetrics();
                int bw = Math.max(18, bm.stringWidth(n) + 10), bh = 18, bx = textWidth + 8, by = 3;
                g2.setColor(Theme.DANGER);
                g2.fill(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
                g2.setColor(Color.WHITE);
                g2.drawString(n, bx + (bw - bm.stringWidth(n)) / 2, by + (bh - bm.getHeight()) / 2 + bm.getAscent());
            }
            g2.dispose();
        }
    }
}
