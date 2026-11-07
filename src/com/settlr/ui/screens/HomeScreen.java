package com.settlr.ui.screens;

import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.model.Household;
import com.settlr.model.OwnedPet;
import com.settlr.model.RecurringBill;
import com.settlr.model.SwapRequest;
import com.settlr.model.SwapStatus;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import com.settlr.model.User;
import com.settlr.service.HouseholdService;
import com.settlr.service.PetService;
import com.settlr.service.SettlementService;
import com.settlr.service.ShopService;
import com.settlr.service.SwapService;
import com.settlr.service.TaskService;
import com.settlr.ui.BaseScreen;
import com.settlr.ui.Navigator;
import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.StatusBadge;
import com.settlr.ui.theme.Theme;
import com.settlr.util.MoneyUtil;
import com.settlr.util.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Home page (sidebar: Home): your tasks on the left, your pet standing in the middle, and
 * money on the right.
 *   - Tasks: overdue and due today (tick them off here), and a swap request waiting for you
 *   - Pet: your XP, the pet in its outfit, its mood, and Edit pet (or "No pet yet" with Go to Shop)
 *   - Money: what you owe or are owed, payments waiting for you to confirm, and the next bill
 * Every number comes from the same services the Tasks, Expenses and Shop pages use.
 */
public class HomeScreen extends BaseScreen {

    private static final String DOT = " \u00B7 ";
    private static final int GAP = 16;
    private static final int CARD_PAD = 22; // left and right padding inside the side cards
    private static final DateTimeFormatter TODAY = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter BILL_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private final TaskService taskService = new TaskService();
    private final SwapService swapService = new SwapService();
    private final SettlementService settlementService = new SettlementService();
    private final HouseholdService householdService = new HouseholdService();
    private final ShopService shopService = new ShopService();
    private final PetService petService = new PetService();

    private final JLabel crumb = label(" ", Font.PLAIN, 12, Theme.MUTED_TEXT);
    private final JLabel hello = label(" ", Font.BOLD, 30, Theme.PRIMARY);
    private final JLabel dateLabel = label(" ", Font.PLAIN, 13, Theme.MUTED_TEXT);
    private final JLabel messageLabel = new JLabel(" ");
    private final JPanel body = new JPanel(new GridBagLayout());
    private final Timer resizeTimer = new Timer(150, e -> rebuildIfSizeChanged());

    // Sizes worked out from the window (see computeSizes); the page is rebuilt when they change
    private int sideWidth = 300;
    private int cardHeight = 520;
    private int figureSize = 290;
    private int centerWidth = 400;

    public HomeScreen() {
        setBackground(Theme.BACKGROUND);
        setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 40, 0, 40)); // the top bar above already leaves room
        JPanel titles = vertical();
        titles.add(left(crumb));
        titles.add(Box.createVerticalStrut(2));
        titles.add(left(hello));
        messageLabel.setFont(Theme.font(Font.PLAIN, 12));
        messageLabel.setBorder(new EmptyBorder(4, 2, 0, 0));
        titles.add(left(messageLabel));
        header.add(titles, BorderLayout.WEST);
        JPanel dateHolder = new JPanel(new BorderLayout());
        dateHolder.setOpaque(false);
        dateHolder.setBorder(new EmptyBorder(0, 0, 22, 0));
        dateHolder.add(dateLabel, BorderLayout.SOUTH);
        header.add(dateHolder, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        body.setOpaque(false);
        body.setBorder(new EmptyBorder(0, 40, 24, 40));
        add(body, BorderLayout.CENTER);
        resizeTimer.setRepeats(false);
        body.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                resizeTimer.restart(); // wait until the window stops changing size
            }
        });
    }

    @Override
    public void onShow() {
        showMessage(" ", false);
        rebuild();
    }

    /**
     * Fits the three columns into the window: side cards between 170 and 320 wide, the pet's
     * column gets the rest (at least 190), and the pet shrinks on short windows.
     */
    private void computeSizes() {
        Insets in = body.getInsets();
        int w = body.getWidth() - in.left - in.right;
        int h = body.getHeight() - in.top - in.bottom;
        if (w <= 0 || h <= 0) {
            return; // not on screen yet: keep the last sizes
        }
        sideWidth = clamp((int) ((w - 2 * GAP) * 0.3), 190, 320);
        centerWidth = w - 2 * sideWidth - 2 * GAP;
        if (centerWidth < 190) {
            sideWidth = Math.max(170, (w - 2 * GAP - 190) / 2);
            centerWidth = w - 2 * sideWidth - 2 * GAP;
        }
        cardHeight = clamp(h, 340, 540);
        figureSize = clamp(Math.min(centerWidth - 10, cardHeight - 225), 110, 300);
    }

    private void rebuildIfSizeChanged() {
        int oldSide = sideWidth, oldHeight = cardHeight, oldFigure = figureSize, oldCenter = centerWidth;
        computeSizes();
        if (oldSide != sideWidth || oldHeight != cardHeight || oldFigure != figureSize
                || Math.abs(oldCenter - centerWidth) > 8) {
            rebuild();
        }
    }

    private void rebuild() {
        computeSizes();
        body.removeAll();
        User me = Session.getInstance().getCurrentUser();
        Household h = Session.getInstance().getCurrentHousehold();
        crumb.setText((h == null ? "" : h.getName() + DOT) + "Home");
        hello.setText("Hi, " + (me == null ? "" : me.getDisplayName()));
        dateLabel.setText(TODAY.format(LocalDate.now()));

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.CENTER;
        c.weighty = 1;
        c.gridx = 0;
        body.add(sized(buildTasksCard()), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, GAP, 0, GAP);
        body.add(buildPetArea(), c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(0, 0, 0, 0);
        body.add(sized(buildMoneyCard()), c);
        body.revalidate();
        body.repaint();
    }

    // =====================================================================
    //  Left: tasks
    // =====================================================================

    private JComponent buildTasksCard() {
        GlassCard card = card();
        List<TaskAssignment> overdue = new ArrayList<>();
        List<TaskAssignment> today = new ArrayList<>();
        List<SwapRequest> swaps;
        TaskAssignment next = null;
        try {
            int myId = me().getId();
            for (TaskAssignment a : taskService.getOverdue()) {
                if (a.getAssigneeId() == myId) {
                    overdue.add(a);
                }
            }
            LocalDateTime now = LocalDateTime.now();
            for (TaskAssignment a : taskService.getMyTasksDueToday()) {
                if (a.getStatus() == TaskStatus.PENDING && !a.getDueAt().isBefore(now)) {
                    today.add(a);
                }
            }
            today.sort(Comparator.comparing(TaskAssignment::getDueAt));
            swaps = new ArrayList<>();
            for (SwapRequest s : swapService.getIncoming()) { // only ones still waiting for an answer
                if (s.getStatus() == SwapStatus.PENDING && s.getAssignmentDueAt().isAfter(now)) {
                    swaps.add(s);
                }
            }
            if (overdue.isEmpty() && today.isEmpty()) {
                next = nextTurn(myId);
            }
        } catch (SettlrException e) {
            return errorCard(card, "Your tasks", e);
        }

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(label("Your tasks", Font.BOLD, 16, Theme.TEXT), BorderLayout.WEST);
        if (!today.isEmpty()) {
            head.add(centered(new StatusBadge(today.size() + " today", StatusBadge.Kind.PENDING)), BorderLayout.EAST);
        }
        card.add(left(fitHeight(head)));

        if (overdue.isEmpty() && today.isEmpty()) {
            card.add(Box.createVerticalGlue());
            JLabel done = label("You're all caught up", Font.BOLD, 15, Theme.MINT);
            card.add(center(done));
            card.add(Box.createVerticalStrut(4));
            String nextText = next == null ? "Nothing else due this week"
                    : "Next: " + next.getTaskTitle() + ", " + TasksScreen.when(next.getDueAt());
            card.add(left(fitHeight(lines(nextText, 12, Theme.MUTED_TEXT, sideWidth - 2 * CARD_PAD, true))));
            if (swaps.isEmpty()) {
                card.add(Box.createVerticalGlue());
            }
        } else {
            if (!overdue.isEmpty()) {
                card.add(group("OVERDUE", Theme.DANGER));
                addTaskRows(card, overdue, cardHeight < 470 ? 1 : 2, true);
            }
            if (!today.isEmpty()) {
                card.add(group("DUE TODAY", Theme.MUTED_TEXT));
                addTaskRows(card, today, cardHeight < 470 ? 2 : 3, false);
            }
        }

        if (!swaps.isEmpty()) {
            if (overdue.isEmpty() && today.isEmpty()) {
                card.add(Box.createVerticalGlue());
            }
            SwapRequest s = swaps.get(0);
            card.add(group(swaps.size() == 1 ? "SWAP REQUEST" : "SWAP REQUESTS (" + swaps.size() + ")",
                    Theme.MUTED_TEXT));
            card.add(left(label(s.getTaskTitle() + DOT + TasksScreen.day(s.getAssignmentDueAt().toLocalDate()),
                    Font.BOLD, 14, Theme.TEXT)));
            card.add(left(label(s.getFromUserName() + " asked you to take it", Font.PLAIN, 12, Theme.MUTED_TEXT)));
            card.add(Box.createVerticalStrut(8));
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            actions.setOpaque(false);
            RoundedButton decline = TasksScreen.smallButton("Decline");
            decline.addActionListener(e -> answerSwap(s, false));
            RoundedButton accept = new RoundedButton("Accept");
            accept.setArc(RoundedButton.PILL);
            accept.setFont(Theme.font(Font.BOLD, 11));
            accept.setMargin(new Insets(0, 0, 0, 0));
            accept.setPreferredSize(decline.getPreferredSize());
            accept.addActionListener(e -> answerSwap(s, true));
            actions.add(decline);
            actions.add(Box.createHorizontalStrut(8));
            actions.add(accept);
            card.add(left(fitHeight(actions)));
        }

        card.add(Box.createVerticalGlue());
        card.add(left(TasksScreen.link("See all tasks \u203A", () -> Navigator.getInstance().show(Navigator.TASKS))));
        return card;
    }

    private void addTaskRows(JPanel card, List<TaskAssignment> list, int max, boolean overdue) {
        for (int i = 0; i < Math.min(max, list.size()); i++) {
            TaskAssignment a = list.get(i);
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(6, 0, 6, 0));
            row.add(centered(new TasksScreen.CheckCircle(TaskStatus.PENDING, overdue, true, () -> complete(a))),
                    BorderLayout.WEST);
            JPanel text = vertical();
            JPanel titleLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            titleLine.setOpaque(false);
            titleLine.add(label(a.getTaskTitle(), Font.BOLD, 14, Theme.TEXT));
            titleLine.add(Box.createHorizontalStrut(6));
            titleLine.add(label(TasksScreen.points(a.getTaskPoints()), Font.BOLD, 11, Theme.AMBER));
            text.add(left(titleLine));
            String when = overdue ? TasksScreen.when(a.getDueAt()) : TasksScreen.time(a.getDueAt());
            text.add(left(label(when, Font.PLAIN, 12, overdue ? Theme.DANGER : Theme.MUTED_TEXT)));
            row.add(text, BorderLayout.CENTER);
            card.add(left(fitHeight(row)));
        }
        if (list.size() > max) {
            card.add(left(label("+" + (list.size() - max) + " more", Font.PLAIN, 12, Theme.MUTED_TEXT)));
        }
    }

    /** Your next pending turn after now (this week or next), including turns not created yet. */
    private TaskAssignment nextTurn(int myId) throws SettlrException {
        LocalDateTime now = LocalDateTime.now();
        TaskAssignment best = null;
        for (LocalDate week : new LocalDate[] {LocalDate.now(), LocalDate.now().plusDays(7)}) {
            List<TaskAssignment> all = new ArrayList<>(taskService.getWeek(week));
            all.addAll(taskService.getPlannedTurns(week));
            for (TaskAssignment a : all) {
                if (a.getAssigneeId() == myId && a.getStatus() == TaskStatus.PENDING && a.getDueAt().isAfter(now)
                        && (best == null || a.getDueAt().isBefore(best.getDueAt()))) {
                    best = a;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    private void complete(TaskAssignment a) {
        boolean late = a.getDueAt().isBefore(LocalDateTime.now());
        try {
            taskService.completeAssignment(a.getId());
        } catch (SettlrException e) {
            rebuild();
            showMessage(e.getMessage(), true);
            return;
        }
        rebuild();
        showMessage(late ? a.getTaskTitle() + " is done (late)."
                : "Nice! " + a.getTaskTitle() + " is done. +" + TasksScreen.points(a.getTaskPoints()), false);
    }

    private void answerSwap(SwapRequest s, boolean accept) {
        try {
            if (accept) {
                swapService.accept(s.getId());
            } else {
                swapService.decline(s.getId());
            }
        } catch (SettlrException e) {
            rebuild();
            showMessage(e.getMessage(), true);
            return;
        }
        rebuild();
        showMessage(accept ? s.getTaskTitle() + " is yours now." : "You declined " + s.getFromUserName() + "'s swap.",
                false);
    }

    // =====================================================================
    //  Middle: the pet
    // =====================================================================

    private JComponent buildPetArea() {
        JPanel area = vertical();
        int balance;
        int thisWeek;
        List<OwnedPet> pets;
        String shownCode;
        PetService.Mood mood = null;
        try {
            balance = shopService.getBalance();
            thisWeek = petService.getXpThisWeek();
            pets = petService.getMyPets();
            shownCode = petService.getShownPetCode(pets);
            if (shownCode != null) {
                mood = petService.getMood();
            }
        } catch (DataAccessException e) {
            e.printStackTrace();
            JLabel l = label("<html><div style='text-align:center;width:260px'>Can't load your pet. If MySQL is "
                    + "running, run database/migrations/004_add_pet_outfits.sql once.</div></html>",
                    Font.PLAIN, 13, Theme.DANGER);
            area.add(Box.createVerticalGlue());
            area.add(center(l));
            area.add(Box.createVerticalGlue());
            return area;
        } catch (SettlrException e) {
            area.add(center(label(e.getMessage(), Font.PLAIN, 13, Theme.DANGER)));
            return area;
        }

        area.add(Box.createVerticalGlue());
        ShopScreen.XpPill pill = new ShopScreen.XpPill();
        pill.setXp(balance);
        area.add(center(pill));
        area.add(Box.createVerticalStrut(5));
        area.add(center(label((thisWeek > 0 ? "+" : "") + thisWeek + " XP this week", Font.PLAIN, 12,
                Theme.MUTED_TEXT)));

        PetFigure figure = new PetFigure(Math.min(centerWidth, figureSize + 10), figureSize);
        area.add(center(figure));

        if (shownCode == null) {
            area.add(center(label("No pet yet", Font.BOLD, 22, Theme.TEXT)));
            area.add(Box.createVerticalStrut(4));
            boolean enough = balance >= com.settlr.model.ShopCatalog.EGG_PRICE;
            area.add(left(fitHeight(lines("Hatch a pet egg in the Shop to get one." + (enough ? " You have enough XP!" : ""),
                    13, Theme.MUTED_TEXT, Math.min(centerWidth - 10, 300), true))));
            area.add(Box.createVerticalStrut(16));
            area.add(center(bigButton("Go to Shop", () -> Navigator.getInstance().show(Navigator.SHOP))));
        } else {
            OwnedPet shown = null;
            for (OwnedPet p : pets) {
                if (p.getPet().getCode().equals(shownCode)) {
                    shown = p;
                }
            }
            figure.setPet(shown);
            area.add(center(fitted(shown.getDisplayName(), 26, Theme.TEXT, centerWidth)));
            area.add(Box.createVerticalStrut(2));
            area.add(center(label(PetEditDialog.kindHtml(shown), Font.PLAIN, 13, Theme.MUTED_TEXT)));
            area.add(Box.createVerticalStrut(12));
            String feeling = (mood.isHappy() ? "happy" : "sad") + ": " + mood.getReason();
            MoodPill moodPill = new MoodPill(shown.getDisplayName() + " is " + feeling, mood.isHappy());
            if (moodPill.getPreferredSize().width > centerWidth) { // narrow window: drop the name
                moodPill = new MoodPill(Character.toUpperCase(feeling.charAt(0)) + feeling.substring(1),
                        mood.isHappy());
            }
            moodPill.setToolTipText(shown.getDisplayName() + " is " + feeling);
            area.add(center(moodPill));
            area.add(Box.createVerticalStrut(16));
            area.add(center(bigButton("Edit pet", () -> editPet(pets, shownCode))));
        }
        area.add(Box.createVerticalGlue());
        return area;
    }

    private void editPet(List<OwnedPet> pets, String shownCode) {
        Set<String> owned;
        try {
            owned = shopService.getOwnedCodes();
        } catch (SettlrException e) {
            showMessage(e.getMessage(), true);
            return;
        }
        PetEditDialog.open(this, pets, shownCode, owned, () -> {
            rebuild();
            showMessage("Saved your pet.", false);
        });
    }

    // =====================================================================
    //  Right: money
    // =====================================================================

    private JComponent buildMoneyCard() {
        GlassCard card = card();
        List<SettlementService.Person> people;
        List<SettlementService.Incoming> waiting;
        List<RecurringBill> bills;
        try {
            people = settlementService.getPeople();
            waiting = settlementService.getWaitingForMe();
            bills = householdService.getBills();
        } catch (SettlrException e) {
            return errorCard(card, "Money", e);
        }

        card.add(left(label("Money", Font.BOLD, 16, Theme.TEXT)));
        card.add(Box.createVerticalStrut(12));

        BigDecimal iOwe = BigDecimal.ZERO;
        BigDecimal owedToMe = BigDecimal.ZERO;
        List<String> owe = new ArrayList<>();
        List<String> owing = new ArrayList<>();
        for (SettlementService.Person p : people) {
            if (p.getNet().signum() < 0) {
                iOwe = iOwe.add(p.getNet().negate());
                owe.add(p.getUser().getDisplayName());
            } else if (p.getNet().signum() > 0) {
                owedToMe = owedToMe.add(p.getNet());
                owing.add(p.getUser().getDisplayName());
            }
        }
        if (iOwe.signum() > 0) {
            card.add(left(label("You owe", Font.PLAIN, 12, Theme.MUTED_TEXT)));
            card.add(left(fitted(whole(iOwe), 26, Theme.DANGER, sideWidth - 2 * CARD_PAD)));
            card.add(left(fitHeight(wrapped("to " + names(owe), 12, Theme.MUTED_TEXT))));
            if (owedToMe.signum() > 0) {
                card.add(Box.createVerticalStrut(4));
                card.add(left(fitHeight(wrapped(names(owing) + (owing.size() == 1 ? " owes" : " owe") + " you "
                        + whole(owedToMe), 12, Theme.MINT))));
            }
        } else if (owedToMe.signum() > 0) {
            card.add(left(label("You're owed", Font.PLAIN, 12, Theme.MUTED_TEXT)));
            card.add(left(fitted(whole(owedToMe), 26, Theme.MINT, sideWidth - 2 * CARD_PAD)));
            card.add(left(fitHeight(wrapped("by " + names(owing), 12, Theme.MUTED_TEXT))));
        } else {
            card.add(left(label("Overall", Font.PLAIN, 12, Theme.MUTED_TEXT)));
            card.add(left(fitted("You're all settled", 24, Theme.MINT, sideWidth - 2 * CARD_PAD)));
            card.add(left(label("Nobody owes anyone", Font.PLAIN, 12, Theme.MUTED_TEXT)));
        }

        if (!waiting.isEmpty()) {
            card.add(group("WAITING FOR YOU", Theme.MUTED_TEXT));
            for (int i = 0; i < Math.min(2, waiting.size()); i++) {
                card.add(left(fitHeight(waitingRow(waiting.get(i)))));
            }
            if (waiting.size() > 2) {
                card.add(left(label("+" + (waiting.size() - 2) + " more on Expenses", Font.PLAIN, 12,
                        Theme.MUTED_TEXT)));
            }
        }

        RecurringBill nextBill = null;
        LocalDate nextDue = null;
        int myId = me().getId();
        for (RecurringBill b : bills) {
            if (!b.isActive() || !b.getMemberIds().contains(myId)) {
                continue;
            }
            LocalDate due = nextDueDate(b.getDueDay());
            if (nextDue == null || due.isBefore(nextDue)) {
                nextBill = b;
                nextDue = due;
            }
        }
        if (nextBill != null) {
            card.add(group("NEXT BILL", Theme.MUTED_TEXT));
            card.add(left(label(nextBill.getTitle(), Font.BOLD, 14, Theme.TEXT)));
            BigDecimal share = nextBill.getAmount()
                    .divide(BigDecimal.valueOf(nextBill.getMemberIds().size()), 2, RoundingMode.HALF_UP);
            String who = nextBill.getPaidBy() == myId ? "you pay it" : "your share " + whole(share);
            card.add(left(fitHeight(wrapped("Due " + BILL_DAY.format(nextDue) + DOT + who, 12, Theme.MUTED_TEXT))));
        }

        card.add(Box.createVerticalGlue());
        card.add(left(TasksScreen.link("Go to Expenses \u203A",
                () -> Navigator.getInstance().show(Navigator.EXPENSES))));
        return card;
    }

    private JPanel waitingRow(SettlementService.Incoming in) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(6, 0, 6, 0));
        String from = in.getFromName();
        BigDecimal amount = in.getSettlement().getAmount();
        row.add(centered(new AvatarLabel(from, 30)), BorderLayout.WEST);
        JPanel text = vertical();
        text.add(left(label(amount.signum() == 0 ? from + " says you're even" : from + " paid " + whole(amount),
                Font.BOLD, 13, Theme.TEXT)));
        text.add(left(label(in.getSummary(), Font.PLAIN, 11, Theme.MUTED_TEXT)));
        row.add(text, BorderLayout.CENTER);
        RoundedButton confirm = RoundedButton.mint("Confirm");
        confirm.setArc(RoundedButton.PILL);
        confirm.setFont(Theme.font(Font.BOLD, 11));
        confirm.setMargin(new Insets(0, 0, 0, 0));
        confirm.setPreferredSize(new Dimension(72, 26));
        int id = in.getSettlement().getId();
        confirm.addActionListener(e -> new ConfirmDialog(amount.signum() == 0
                ? "Are you and " + from + " even?" : "Did " + from + " pay you " + whole(amount) + "?",
                "This settles everything between you two. To mark it as not received, go to Expenses.")
                .buttons("Not yet", "Yes, confirm", false, () -> {
                    try {
                        settlementService.confirm(id);
                    } catch (SettlrException ex) {
                        rebuild();
                        showMessage(ex.getMessage(), true);
                        return;
                    }
                    rebuild();
                    showMessage("Confirmed. You and " + from + " are settled up.", false);
                })
                .showOver(this));
        row.add(centered(confirm), BorderLayout.EAST);
        return row;
    }

    /** The bill's next due date from today: this month if the day hasn't passed, else next month. */
    private static LocalDate nextDueDate(int dueDay) {
        LocalDate today = LocalDate.now();
        LocalDate thisMonth = today.withDayOfMonth(Math.min(dueDay, today.lengthOfMonth()));
        if (!thisMonth.isBefore(today)) {
            return thisMonth;
        }
        LocalDate next = today.plusMonths(1);
        return next.withDayOfMonth(Math.min(dueDay, next.lengthOfMonth()));
    }

    // =====================================================================
    //  Small pieces
    // =====================================================================

    private JComponent errorCard(GlassCard card, String title, SettlrException e) {
        card.add(left(label(title, Font.BOLD, 16, Theme.TEXT)));
        card.add(Box.createVerticalStrut(10));
        String text = e instanceof DataAccessException ? "Can't load this right now. Is MySQL running?" : e.getMessage();
        card.add(left(fitHeight(wrapped(text, 12, Theme.DANGER))));
        if (e instanceof DataAccessException) {
            e.printStackTrace();
        }
        return card;
    }

    private void showMessage(String text, boolean error) {
        messageLabel.setForeground(error ? Theme.DANGER : Theme.MINT);
        messageLabel.setText(text);
    }

    private static User me() {
        return Session.getInstance().getCurrentUser();
    }

    /** P1,250 when it's a whole number, P1,250.50 otherwise. */
    private static String whole(BigDecimal amount) {
        String s = MoneyUtil.format(amount);
        return s.endsWith(".00") ? s.substring(0, s.length() - 3) : s;
    }

    /** "Ben", "Ben and Carlo", "Ben, Carlo and Dani". */
    private static String names(List<String> list) {
        if (list.size() <= 1) {
            return String.join("", list);
        }
        return String.join(", ", list.subList(0, list.size() - 1)) + " and " + list.get(list.size() - 1);
    }

    private static GlassCard card() {
        GlassCard card = new GlassCard(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(20, CARD_PAD, 18, CARD_PAD));
        return card;
    }

    /** Text that wraps inside a side card. */
    private JComponent wrapped(String text, int size, Color color) {
        return lines(text, size, color, sideWidth - 2 * CARD_PAD, false);
    }

    /**
     * Breaks text into lines that fit maxWidth, one label per line (measured with the real font,
     * so it never gets cut off the way HTML labels sometimes do).
     */
    private static JComponent lines(String text, int size, Color color, int maxWidth, boolean centered) {
        JPanel p = vertical();
        Font font = Theme.font(Font.PLAIN, size);
        FontMetrics fm = p.getFontMetrics(font);
        StringBuilder line = new StringBuilder();
        List<String> out = new ArrayList<>();
        for (String word : text.split(" ")) {
            String tryLine = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(tryLine) <= maxWidth || line.length() == 0) {
                line.setLength(0);
                line.append(tryLine);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        out.add(line.toString());
        for (String l : out) {
            JLabel label = label(l, Font.PLAIN, size, color);
            if (centered) {
                label.setAlignmentX(Component.CENTER_ALIGNMENT);
                label.setHorizontalAlignment(SwingConstants.CENTER);
            }
            p.add(centered ? label : left(label));
        }
        if (centered) {
            p.setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        return p;
    }

    /** A bold label whose font gets smaller (down to 15) until it fits the width. */
    private static JLabel fitted(String text, int size, Color color, int maxWidth) {
        JLabel l = label(text, Font.BOLD, size, color);
        while (size > 15 && l.getFontMetrics(l.getFont()).stringWidth(text) > maxWidth) {
            size--;
            l.setFont(Theme.font(Font.BOLD, size));
        }
        return l;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private JComponent sized(JComponent c) {
        Dimension d = new Dimension(sideWidth, cardHeight);
        c.setPreferredSize(d);
        c.setMinimumSize(d);
        c.setMaximumSize(d);
        return c;
    }

    private static JComponent group(String text, Color color) {
        JLabel l = label(text, Font.BOLD, 11, color);
        l.setBorder(new EmptyBorder(16, 0, 4, 0));
        return left(l);
    }

    private static RoundedButton bigButton(String text, Runnable action) {
        RoundedButton b = new RoundedButton(text);
        b.setArc(RoundedButton.PILL);
        b.setFont(Theme.font(Font.BOLD, 14));
        b.setPreferredSize(new Dimension(b.getFontMetrics(b.getFont()).stringWidth(text) + 56, 42));
        b.addActionListener(e -> action.run());
        return b;
    }

    private static JLabel label(String text, int style, int size, Color color) {
        return ShopScreen.label(text, style, size, color);
    }

    private static JPanel vertical() {
        return ShopScreen.vertical();
    }

    private static JPanel centered(JComponent c) {
        return ShopScreen.centered(c);
    }

    /** Centers a component horizontally inside a vertical BoxLayout. */
    private static JComponent center(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        p.setOpaque(false);
        p.add(c);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        p.setAlignmentX(Component.LEFT_ALIGNMENT); // same as the rows around it, or BoxLayout shifts them
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

    /** "Mochi is happy: ..." in a soft mint pill, or red when sad. */
    private static final class MoodPill extends JLabel {
        private final Color tint;

        MoodPill(String text, boolean happy) {
            super(text);
            tint = happy ? Theme.MINT : Theme.DANGER;
            setFont(Theme.font(Font.BOLD, 12));
            setForeground(tint);
            setBorder(new EmptyBorder(5, 13, 5, 13));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 38));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
