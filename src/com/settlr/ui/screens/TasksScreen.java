package com.settlr.ui.screens;

import com.settlr.dao.MembershipDao;
import com.settlr.exception.SettlrException;
import com.settlr.model.Household;
import com.settlr.model.RecurringTask;
import com.settlr.model.SwapRequest;
import com.settlr.model.SwapStatus;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import com.settlr.model.User;
import com.settlr.service.SwapService;
import com.settlr.service.TaskService;
import com.settlr.ui.BaseScreen;
import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.SegmentedControl;
import com.settlr.ui.components.SlimScrollBarUI;
import com.settlr.ui.components.StatusBadge;
import com.settlr.ui.theme.Theme;
import com.settlr.util.DateTimeUtil;
import com.settlr.util.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tasks page:
 *   - a bar for swap requests waiting for YOU (only when there are some)
 *   - the week's tasks grouped by when they're due (Overdue, Today, Tomorrow, Later this week),
 *     either just yours ("Mine") or the whole household's ("Everyone"); tick the circle to finish one
 *   - on the right: how your week is going, and everyone's progress
 * Clicking a task opens its details (TaskDetailDialog); Swap asks someone to take it (SwapDialog).
 * The rules live in TaskService and SwapService; this screen only shows them.
 */
public class TasksScreen extends BaseScreen {

    /** One service call that can fail with a readable message. */
    private interface Action {
        void run() throws SettlrException;
    }

    static final String DOT = " \u00B7 ";
    private static final Color DIVIDER = new Color(232, 217, 205, 40);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_HEADER = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH);
    /** Tasks from the Add task form are due at 23:59, which reads better as "End of day". */
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59);

    private final TaskService taskService = new TaskService();
    private final SwapService swapService = new SwapService();
    private final MembershipDao membershipDao = new MembershipDao();

    // what's loaded
    private final Map<Integer, User> members = new LinkedHashMap<>();
    private final Map<Integer, Task> tasks = new HashMap<>();
    private final Map<Integer, SwapRequest> myPendingSwaps = new HashMap<>(); // by assignment id
    private List<TaskAssignment> week = new ArrayList<>();
    private List<TaskAssignment> overdue = new ArrayList<>();

    // what's picked
    private int weekOffset;          // 0 = this week, -1 = last week, 1 = next week
    private boolean everyone;

    private final JLabel crumb = label(" ", Font.PLAIN, 12, Theme.MUTED_TEXT);
    private final JLabel messageLabel = new JLabel(" ");
    private final JPanel attentionCard = new AttentionCard();
    private final JPanel rows = vertical();
    private final JLabel weekTitle = label(" ", Font.BOLD, 12, Theme.TEXT);
    private final JLabel weekRange = label(" ", Font.PLAIN, 12, Theme.MUTED_TEXT);
    private final GlassCard yourWeekCard = new GlassCard(null);
    private final GlassCard householdCard = new GlassCard(null);
    private final SegmentedControl mineOrEveryone = new SegmentedControl(new String[] {"Mine", "Everyone"}, i -> {
        everyone = i == 1;
        showRows();
    });

    public TasksScreen() {
        setBackground(Theme.BACKGROUND);
        setLayout(new BorderLayout());

        JPanel page = vertical();
        page.setBorder(new EmptyBorder(0, 40, 24, 40)); // the top bar above already leaves room
        page.add(left(buildHeader()));
        page.add(left(messageLabel));
        page.add(left(attentionCard));
        page.add(left(buildColumns()));

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
        titles.add(left(label("Tasks", Font.BOLD, 30, Theme.PRIMARY)));

        RoundedButton add = RoundedButton.mint("+  Add task");
        add.setArc(RoundedButton.PILL);
        add.setFont(Theme.font(Font.BOLD, 13));
        add.setPreferredSize(new Dimension(130, 38));
        add.addActionListener(e -> TaskFormDialog.open(this, task -> {
            reload();
            showMessage("Added \"" + task.getTitle() + "\".", false);
        }));
        JPanel addHolder = new JPanel(new GridBagLayout());
        addHolder.setOpaque(false);
        addHolder.add(add);

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(titles, BorderLayout.WEST);
        header.add(addHolder, BorderLayout.EAST);

        messageLabel.setFont(Theme.font(Font.PLAIN, 12));
        messageLabel.setBorder(new EmptyBorder(6, 2, 0, 0));
        return header;
    }

    private JPanel buildColumns() {
        // left: the list
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        nav.setOpaque(false);
        nav.add(arrow("\u2039", -1));
        nav.add(weekTitle);
        nav.add(weekRange);
        nav.add(arrow("\u203A", 1));
        JPanel navHolder = new JPanel(new GridBagLayout());
        navHolder.setOpaque(false);
        navHolder.add(nav);

        JPanel listHead = new JPanel(new BorderLayout());
        listHead.setOpaque(false);
        listHead.setBorder(new EmptyBorder(14, 0, 4, 0));
        listHead.add(mineOrEveryone, BorderLayout.WEST);
        listHead.add(navHolder, BorderLayout.EAST);

        GlassCard listCard = new GlassCard(new BorderLayout());
        listCard.setBorder(new EmptyBorder(0, 20, 10, 20));
        listCard.add(listHead, BorderLayout.NORTH);
        listCard.add(rows, BorderLayout.CENTER);

        // right: progress
        for (GlassCard c : new GlassCard[] {yourWeekCard, householdCard}) {
            c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
            c.setBorder(new EmptyBorder(14, 20, 14, 20));
        }
        JPanel right = vertical();
        right.add(left(yourWeekCard));
        right.add(Box.createVerticalStrut(16));
        right.add(left(householdCard));

        JPanel columns = new JPanel(new GridBagLayout());
        columns.setOpaque(false);
        columns.setBorder(new EmptyBorder(14, 0, 0, 0));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.NORTH;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.weightx = 1.65;
        c.insets = new Insets(0, 0, 0, 18);
        columns.add(narrow(listCard), c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        columns.add(narrow(right), c);
        return columns;
    }

    private JLabel arrow(String text, int step) {
        JLabel a = label(text, Font.BOLD, 18, Theme.MUTED_TEXT);
        a.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        a.setBorder(new EmptyBorder(0, 4, 2, 4));
        a.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { weekOffset += step; reload(); }
            @Override public void mouseEntered(MouseEvent e) { a.setForeground(Theme.TEXT); }
            @Override public void mouseExited(MouseEvent e) { a.setForeground(Theme.MUTED_TEXT); }
        });
        return a;
    }

    // =====================================================================
    //  Loading
    // =====================================================================

    @Override
    public void onShow() {
        Household h = Session.getInstance().getCurrentHousehold();
        crumb.setText((h == null ? "" : h.getName() + DOT) + "Tasks");
        weekOffset = 0;
        showMessage(" ", false);
        reload();
    }

    /** Reloads everything without clearing the message line, so a result message survives. */
    void reload() {
        try {
            Household h = Session.getInstance().getCurrentHousehold();
            members.clear();
            if (h != null) {
                for (User u : membershipDao.findActiveMembers(h.getId())) {
                    members.put(u.getId(), u);
                }
            }
            tasks.clear();
            for (Task t : taskService.getActiveTasks()) {
                tasks.put(t.getId(), t);
            }
            week = new ArrayList<>(taskService.getWeek(weekStart()));
            week.addAll(taskService.getPlannedTurns(weekStart())); // turns of repeating tasks not created yet
            week.sort(Comparator.comparing(TaskAssignment::getDueAt));
            overdue = weekOffset == 0 ? taskService.getOverdue() : new ArrayList<>();
            myPendingSwaps.clear();
            for (SwapRequest s : swapService.getOutgoing()) {
                if (s.getStatus() == SwapStatus.PENDING) {
                    myPendingSwaps.put(s.getAssignmentId(), s);
                }
            }
            loadAttention();
        } catch (SettlrException e) {
            week = new ArrayList<>();
            overdue = new ArrayList<>();
            attentionCard.setVisible(false);
            showMessage(e.getMessage(), true);
        }
        LocalDate start = weekStart();
        weekTitle.setText(weekOffset == 0 ? "This week" : weekOffset == -1 ? "Last week"
                : weekOffset == 1 ? "Next week" : "Week of " + DAY.format(start));
        weekRange.setText(DAY.format(start) + " \u2013 " + (start.plusDays(6).getMonth() == start.getMonth()
                ? String.valueOf(start.plusDays(6).getDayOfMonth()) : DAY.format(start.plusDays(6))));
        showRows();
        fillYourWeek();
        fillHousehold();
        revalidate();
        repaint();
    }

    private LocalDate weekStart() {
        return DateTimeUtil.weekStart(LocalDate.now()).toLocalDate().plusWeeks(weekOffset);
    }

    private void loadAttention() throws SettlrException {
        attentionCard.removeAll();
        List<SwapRequest> waiting = new ArrayList<>();
        for (SwapRequest s : swapService.getIncoming()) {
            if (s.getStatus() == SwapStatus.PENDING && s.getAssignmentDueAt().isAfter(LocalDateTime.now())) {
                waiting.add(s);
            }
        }
        for (int i = 0; i < waiting.size(); i++) {
            attentionCard.add(left(new SwapRow(waiting.get(i), i < waiting.size() - 1)));
        }
        attentionCard.setVisible(!waiting.isEmpty());
    }

    // =====================================================================
    //  The list
    // =====================================================================

    private void showRows() {
        rows.removeAll();
        int me = me().getId();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        List<TaskAssignment> overdueShown = new ArrayList<>();
        for (TaskAssignment a : overdue) {
            if (everyone || a.getAssigneeId() == me) {
                overdueShown.add(a);
            }
        }
        // group the week (overdue ones are already in their own group)
        Map<String, List<TaskAssignment>> groups = new LinkedHashMap<>();
        if (weekOffset == 0) {
            groups.put("Today" + DOT + DAY_HEADER.format(today), new ArrayList<>());
            groups.put("Tomorrow", new ArrayList<>());
            groups.put("Later this week", new ArrayList<>());
            groups.put("Earlier this week", new ArrayList<>());
        }
        for (TaskAssignment a : week) {
            if (!everyone && a.getAssigneeId() != me) {
                continue;
            }
            if (a.getStatus() == TaskStatus.PENDING && a.getDueAt().isBefore(now)) {
                if (!containsId(overdueShown, a)) {
                    overdueShown.add(a);
                }
                continue;
            }
            LocalDate day = a.getDueAt().toLocalDate();
            String key;
            if (weekOffset != 0) {
                key = DAY_HEADER.format(day);
            } else if (day.equals(today)) {
                key = "Today" + DOT + DAY_HEADER.format(today);
            } else if (day.equals(today.plusDays(1))) {
                key = "Tomorrow";
            } else if (day.isAfter(today)) {
                key = "Later this week";
            } else {
                key = "Earlier this week";
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
        }

        if (!overdueShown.isEmpty()) {
            overdueShown.sort(Comparator.comparing(TaskAssignment::getDueAt));
            addGroup("Overdue", overdueShown, Theme.DANGER);
        }
        boolean any = !overdueShown.isEmpty();
        for (Map.Entry<String, List<TaskAssignment>> g : groups.entrySet()) {
            if (!g.getValue().isEmpty()) {
                addGroup(g.getKey(), g.getValue(), Theme.MUTED_TEXT);
                any = true;
            }
        }
        if (!any) {
            JLabel empty = label(weekOffset > 0 ? "Nothing planned for that week yet."
                    : everyone ? "Nothing is due this week." : "Nothing for you this week. Enjoy!",
                    Font.PLAIN, 13, Theme.MUTED_TEXT);
            empty.setBorder(new EmptyBorder(18, 0, 18, 0));
            rows.add(left(empty));
        }
        rows.revalidate();
        rows.repaint();
    }

    private void addGroup(String title, List<TaskAssignment> list, Color color) {
        JLabel head = label(title.toUpperCase(Locale.ENGLISH), Font.BOLD, 11, color);
        head.setBorder(new EmptyBorder(14, 0, 2, 0));
        rows.add(left(head));
        for (int i = 0; i < list.size(); i++) {
            rows.add(left(new TaskRow(list.get(i), i < list.size() - 1)));
        }
    }

    private static boolean containsId(List<TaskAssignment> list, TaskAssignment a) {
        for (TaskAssignment x : list) {
            if (x.getId() == a.getId()) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    //  The cards on the right
    // =====================================================================

    private void fillYourWeek() {
        yourWeekCard.removeAll();
        int me = me().getId();
        int total = 0, done = 0, earned = 0, left = 0;
        for (TaskAssignment a : week) {
            if (a.getAssigneeId() != me) {
                continue;
            }
            total++;
            if (isDone(a)) {
                done++;
                earned += a.getTaskPoints();
            } else if (a.getStatus() == TaskStatus.PENDING) {
                left += a.getTaskPoints();
            }
        }
        int late = 0;
        for (TaskAssignment a : overdue) {
            if (a.getAssigneeId() == me) {
                late++;
            }
        }

        yourWeekCard.add(left(label(weekOffset == 0 ? "Your week" : "Your " + weekTitle.getText().toLowerCase(),
                Font.PLAIN, 12, Theme.MUTED_TEXT)));
        yourWeekCard.add(Box.createVerticalStrut(4));
        if (total == 0) {
            yourWeekCard.add(left(label("Nothing on your plate", Font.BOLD, 18, Theme.TEXT)));
        } else {
            JPanel big = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            big.setOpaque(false);
            big.add(label(done + " of " + total, Font.BOLD, 26, Theme.TEXT));
            big.add(label("  done", Font.PLAIN, 13, Theme.MUTED_TEXT));
            yourWeekCard.add(left(fitHeight(big)));
            yourWeekCard.add(Box.createVerticalStrut(10));
            yourWeekCard.add(left(new ProgressBar(done, total, 7)));
            yourWeekCard.add(Box.createVerticalStrut(10));
            JPanel pts = new JPanel(new BorderLayout());
            pts.setOpaque(false);
            pts.add(twoTone(earned + (earned == 1 ? " pt" : " pts"), " earned", Theme.AMBER), BorderLayout.WEST);
            pts.add(twoTone(left + (left == 1 ? " pt" : " pts"), weekOffset < 0 ? " not done" : " to go",
                    Theme.TEXT), BorderLayout.EAST);
            yourWeekCard.add(left(fitHeight(pts)));
        }
        if (late > 0) {
            yourWeekCard.add(Box.createVerticalStrut(8));
            yourWeekCard.add(left(label(late + " overdue" + DOT + "finishing it now counts as late",
                    Font.PLAIN, 12, Theme.DANGER)));
        }
    }

    private void fillHousehold() {
        householdCard.removeAll();
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(label(weekOffset == 0 ? "Household this week" : "Household", Font.BOLD, 14, Theme.TEXT),
                BorderLayout.WEST);
        householdCard.add(left(fitHeight(head)));
        householdCard.add(Box.createVerticalStrut(4));

        Map<Integer, int[]> counts = new HashMap<>(); // user id -> {done, total}
        for (User u : members.values()) {
            counts.put(u.getId(), new int[2]);
        }
        for (TaskAssignment a : week) {
            int[] c = counts.get(a.getAssigneeId());
            if (c != null) {
                c[1]++;
                if (isDone(a)) {
                    c[0]++;
                }
            }
        }
        List<User> order = new ArrayList<>(members.values());
        order.sort(Comparator.<User>comparingDouble(u -> {
            int[] c = counts.get(u.getId());
            return c[1] == 0 ? -1 : -(double) c[0] / c[1];
        }).thenComparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        for (User u : order) {
            householdCard.add(left(new MemberProgress(u, counts.get(u.getId()))));
        }
    }

    // =====================================================================
    //  Actions
    // =====================================================================

    private void complete(TaskAssignment a) {
        try {
            taskService.completeAssignment(a.getId());
        } catch (SettlrException e) {
            reload();
            showMessage(e.getMessage(), true);
            return;
        }
        boolean late = a.getDueAt().isBefore(LocalDateTime.now());
        reload();
        showMessage(late ? a.getTaskTitle() + " is done (late)."
                : "Nice! " + a.getTaskTitle() + " is done. +" + a.getTaskPoints()
                        + (a.getTaskPoints() == 1 ? " pt" : " pts"), false);
    }

    private void openDetails(TaskAssignment a) {
        TaskDetailDialog.open(this, a, tasks.get(a.getTaskId()), myPendingSwaps.get(a.getId()), new TaskDetailDialog.Listener() {
            @Override public void completed() { complete(a); }
            @Override public void askSwap() { openSwap(a); }
            @Override public void archived(String title) {
                reload();
                showMessage("Archived \"" + title + "\". Its past turns stay in the history.", false);
            }
        });
    }

    private void openSwap(TaskAssignment a) {
        Map<Integer, Integer> left = new HashMap<>(); // pending tasks this week per person
        for (TaskAssignment x : week) {
            if (x.getStatus() == TaskStatus.PENDING) {
                left.merge(x.getAssigneeId(), 1, Integer::sum);
            }
        }
        List<User> others = new ArrayList<>();
        for (User u : members.values()) {
            if (u.getId() != me().getId()) {
                others.add(u);
            }
        }
        SwapDialog.open(this, a, others, left, name -> {
            reload();
            showMessage("Asked " + name + " to take " + a.getTaskTitle() + ". It's yours until they accept.", false);
        });
    }

    /** Asks first, then runs the action, reloads and shows the result. */
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
    //  Text helpers (also used by the popups)
    // =====================================================================

    private static User me() {
        return Session.getInstance().getCurrentUser();
    }

    /** A future turn of a repeating task that the app hasn't created yet (see TaskService.getPlannedTurns). */
    static boolean isPlanned(TaskAssignment a) {
        return a.getId() == 0;
    }

    private static boolean isDone(TaskAssignment a) {
        return a.getStatus() == TaskStatus.DONE || a.getStatus() == TaskStatus.DONE_LATE;
    }

    /** "8:00 PM", or "End of day" for tasks due at 23:59. */
    static String time(LocalDateTime t) {
        return t.toLocalTime().equals(END_OF_DAY) ? "End of day" : TIME.format(t);
    }

    /** "Today", "Tomorrow", "Yesterday", "Thu" (within a week) or "Nov 12". */
    static String day(LocalDate d) {
        LocalDate today = LocalDate.now();
        if (d.equals(today)) return "Today";
        if (d.equals(today.plusDays(1))) return "Tomorrow";
        if (d.equals(today.minusDays(1))) return "Yesterday";
        if (d.isAfter(today) && d.isBefore(today.plusDays(7))) return WEEKDAY.format(d);
        return DAY.format(d);
    }

    /** "Today \u00B7 8:00 PM", "Thu \u00B7 End of day". */
    static String when(LocalDateTime t) {
        return day(t.toLocalDate()) + DOT + time(t);
    }

    /** "Daily", "Weekly", "Monthly" or "One-time". */
    static String repeats(Task t) {
        if (t instanceof RecurringTask) {
            switch (((RecurringTask) t).getRecurrence()) {
                case DAILY:  return "Daily";
                case WEEKLY: return "Weekly";
                default:     return "Monthly";
            }
        }
        return "One-time";
    }

    static boolean rotates(Task t) {
        return t instanceof RecurringTask && ((RecurringTask) t).isRotates();
    }

    static String points(int p) {
        return p + (p == 1 ? " pt" : " pts");
    }

    // =====================================================================
    //  Small helpers
    // =====================================================================

    static JPanel vertical() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    static <T extends JComponent> T fitHeight(T c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        return l;
    }

    static JLabel link(String text, Runnable action) {
        JLabel l = label(text, Font.BOLD, 12, Theme.PRIMARY);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { action.run(); }
            @Override public void mouseEntered(MouseEvent e) { l.setText("<html><u>" + text + "</u></html>"); }
            @Override public void mouseExited(MouseEvent e) { l.setText(text); }
        });
        return l;
    }

    static RoundedButton smallButton(String text) {
        RoundedButton b = RoundedButton.outline(text);
        b.setArc(RoundedButton.PILL);
        b.setFont(Theme.font(Font.BOLD, 11));
        b.setMargin(new Insets(0, 0, 0, 0));
        b.setPreferredSize(new Dimension(b.getFontMetrics(b.getFont()).stringWidth(text) + 26, 26));
        return b;
    }

    /** "2 pts earned": the first part bold in a color, the rest grey. */
    private static JPanel twoTone(String strong, String rest, Color color) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.add(label(strong, Font.BOLD, 12, color));
        p.add(label(rest, Font.PLAIN, 12, Theme.MUTED_TEXT));
        return p;
    }

    private static JPanel centeredVertically(JComponent c) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.add(c);
        return p;
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
    //  Pieces of the page
    // =====================================================================

    /** One task: tick circle, (avatar), title + points, details, when, and Swap. Click it for details. */
    private class TaskRow extends JPanel {
        private final boolean divider;
        private boolean hover;

        TaskRow(TaskAssignment a, boolean divider) {
            super(new BorderLayout(12, 0));
            this.divider = divider;
            setOpaque(false);
            setBorder(new EmptyBorder(8, 0, 8, 0));
            int me = me().getId();
            Task task = tasks.get(a.getTaskId());
            boolean mine = a.getAssigneeId() == me;
            boolean pastDue = a.getStatus() == TaskStatus.PENDING && a.getDueAt().isBefore(LocalDateTime.now());
            boolean canTick;
            try {
                canTick = taskService.canComplete(a);
            } catch (SettlrException e) {
                canTick = false;
            }

            // left: tick + (avatar)
            CheckCircle check = new CheckCircle(a.getStatus(), pastDue, canTick, () -> complete(a));
            JPanel lefty = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            lefty.setOpaque(false);
            lefty.add(check);
            if (everyone) {
                lefty.add(Box.createHorizontalStrut(10));
                User u = members.get(a.getAssigneeId());
                lefty.add(u != null ? new AvatarLabel(u, 26) : new AvatarLabel(a.getAssigneeName(), 26));
            }

            // middle: title, points, details
            JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            title.setOpaque(false);
            boolean finished = isDone(a);
            String t = a.getTaskTitle();
            title.add(label(finished ? "<html><s>" + t + "</s></html>" : t, Font.BOLD, 14,
                    finished ? Theme.MUTED_TEXT : Theme.TEXT));
            title.add(Box.createHorizontalStrut(8));
            title.add(new StatusBadge(points(a.getTaskPoints()), StatusBadge.Kind.PENDING));

            List<String> bits = new ArrayList<>();
            if (everyone) {
                bits.add(mine ? "You" : a.getAssigneeName());
            }
            if (isPlanned(a)) {
                bits.add("Coming up");
            }
            if (task != null) {
                bits.add(repeats(task) + (rotates(task) ? ", turns rotate" : ""));
                if (!everyone && task.getDescription() != null && task.getDescription().length() <= 32) {
                    bits.add(task.getDescription());
                }
            }
            JPanel text = vertical();
            text.add(left(title));
            text.add(Box.createVerticalStrut(2));
            text.add(left(label(String.join(DOT, bits), Font.PLAIN, 11, Theme.MUTED_TEXT)));

            // right: when / status, and Swap
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
            right.setOpaque(false);
            right.add(statusLabel(a, pastDue));
            SwapRequest asked = myPendingSwaps.get(a.getId());
            if (asked != null) {
                right.add(new StatusBadge("Asked " + asked.getToUserName(), StatusBadge.Kind.PENDING));
            } else if (mine && !isPlanned(a) && a.getStatus() == TaskStatus.PENDING && !pastDue) {
                RoundedButton swap = smallButton("Swap");
                swap.addActionListener(e -> openSwap(a));
                right.add(swap);
            }

            add(centeredVertically(lefty), BorderLayout.WEST);
            add(leftCentered(text), BorderLayout.CENTER);
            add(centeredVertically(right), BorderLayout.EAST);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));

            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Details");
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { openDetails(a); }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        private JLabel statusLabel(TaskAssignment a, boolean pastDue) {
            switch (a.getStatus()) {
                case DONE:      return label("Done", Font.PLAIN, 12, Theme.MINT);
                case DONE_LATE: return label("Done late", Font.PLAIN, 12, Theme.AMBER);
                case MISSED:    return label("Missed", Font.PLAIN, 12, Theme.DANGER);
                default:
                    if (pastDue) {
                        LocalDate d = a.getDueAt().toLocalDate();
                        return label(d.equals(LocalDate.now()) ? time(a.getDueAt()) : day(d), Font.PLAIN, 12,
                                Theme.DANGER);
                    }
                    LocalDate d = a.getDueAt().toLocalDate();
                    boolean soon = d.equals(LocalDate.now()) || d.equals(LocalDate.now().plusDays(1)) || weekOffset != 0;
                    return label(soon ? time(a.getDueAt()) : when(a.getDueAt()), Font.PLAIN, 12, Theme.TEXT);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (hover) {
                g2.setColor(new Color(255, 255, 255, 10));
                g2.fill(new RoundRectangle2D.Float(-8, 2, getWidth() + 16, getHeight() - 4, 14, 14));
            }
            if (divider) {
                g2.setColor(DIVIDER);
                g2.fillRect(0, getHeight() - 1, getWidth(), 1);
            }
            g2.dispose();
        }
    }

    /** The round tick button. Mint when done, amber when done late, red ring when overdue or missed. */
    static final class CheckCircle extends JComponent {
        private static final int SIZE = 22;
        private final TaskStatus status;
        private final boolean overdue;
        private final boolean clickable;
        private boolean hover;

        CheckCircle(TaskStatus status, boolean overdue, boolean clickable, Runnable onTick) {
            this.status = status;
            this.overdue = overdue;
            this.clickable = clickable;
            Dimension d = new Dimension(SIZE + 2, SIZE + 2);
            setPreferredSize(d);
            setMinimumSize(d);
            if (clickable) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("Mark as done");
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { onTick.run(); }
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                });
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float x = 1, y = (getHeight() - SIZE) / 2f;
            Shape circle = new Ellipse2D.Float(x, y, SIZE, SIZE);
            boolean done = status == TaskStatus.DONE || status == TaskStatus.DONE_LATE;
            if (done) {
                g2.setColor(status == TaskStatus.DONE ? Theme.MINT : Theme.AMBER);
                g2.fill(circle);
                drawTick(g2, x, y, Theme.ON_MINT);
            } else {
                Color ring = status == TaskStatus.MISSED || overdue ? Theme.DANGER
                        : clickable ? new Color(232, 217, 205, 150) : new Color(232, 217, 205, 60);
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(ring);
                g2.draw(new Ellipse2D.Float(x + 1, y + 1, SIZE - 2, SIZE - 2));
                if (hover) {
                    g2.setColor(new Color(95, 208, 176, 60));
                    g2.fill(circle);
                    drawTick(g2, x, y, Theme.MINT);
                }
            }
            g2.dispose();
        }

        private static void drawTick(Graphics2D g2, float x, float y, Color color) {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawPolyline(new int[] {(int) x + 6, (int) x + 10, (int) x + 16},
                    new int[] {(int) y + 11, (int) y + 15, (int) y + 8}, 3);
        }
    }

    /** A thin rounded bar filled mint for done/total. */
    static final class ProgressBar extends JComponent {
        private final int done, total;

        ProgressBar(int done, int total, int height) {
            this.done = done;
            this.total = total;
            setPreferredSize(new Dimension(10, height));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            g2.setColor(new Color(255, 255, 255, 25));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), h, h, h));
            if (total > 0 && done > 0) {
                g2.setColor(Theme.MINT);
                g2.fill(new RoundRectangle2D.Float(0, 0, Math.max(h, getWidth() * done / (float) total), h, h, h));
            }
            g2.dispose();
        }
    }

    /** "Carlo ... 3/3" with a bar under the name. */
    private class MemberProgress extends JPanel {
        MemberProgress(User u, int[] count) {
            super(new BorderLayout(10, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(6, 0, 6, 0));
            boolean me = u.getId() == me().getId();
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            String name = me ? "You" : u.getDisplayName();
            boolean all = count[1] > 0 && count[0] == count[1];
            top.add(label(name + (all ? "  \u2713" : ""), Font.BOLD, 13, all ? Theme.MINT : Theme.TEXT),
                    BorderLayout.WEST);
            top.add(label(count[1] == 0 ? "no tasks" : count[0] + "/" + count[1], Font.PLAIN, 12, Theme.MUTED_TEXT),
                    BorderLayout.EAST);
            JPanel text = vertical();
            text.add(left(fitHeight(top)));
            text.add(Box.createVerticalStrut(5));
            text.add(left(new ProgressBar(count[0], count[1], 5)));
            add(centeredVertically(new AvatarLabel(u, 26)), BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        }
    }

    /** "Carlo asked you to take Wash Dishes / Tue \u00B7 8:00 PM \u00B7 1h ago   Decline [Accept]". */
    private class SwapRow extends JPanel {
        SwapRow(SwapRequest s, boolean divider) {
            super(new BorderLayout(12, 0));
            setOpaque(false);
            setBorder(new EmptyBorder(9, 0, 9, 0));
            String from = s.getFromUserName();
            JPanel text = vertical();
            text.add(left(label(from + " asked you to take " + s.getTaskTitle(), Font.BOLD, 14, Theme.TEXT)));
            text.add(Box.createVerticalStrut(2));
            text.add(left(label(when(s.getAssignmentDueAt()) + DOT + DateTimeUtil.timeAgo(s.getCreatedAt()),
                    Font.PLAIN, 11, Theme.MUTED_TEXT)));

            JLabel decline = link("Decline", () -> confirmThen("Decline " + from + "'s swap?\n"
                            + s.getTaskTitle() + " stays with " + from + ".", "Decline swap",
                    () -> swapService.decline(s.getId()), "Declined. " + s.getTaskTitle() + " stays with " + from + "."));
            RoundedButton accept = new RoundedButton("Accept");
            accept.setArc(RoundedButton.PILL);
            accept.setFont(Theme.font(Font.BOLD, 12));
            accept.setMargin(new Insets(0, 0, 0, 0));
            accept.setPreferredSize(new Dimension(84, 30));
            accept.addActionListener(e -> confirmThen("Take " + s.getTaskTitle() + " (" + when(s.getAssignmentDueAt())
                            + ") from " + from + "?", "Accept swap", () -> swapService.accept(s.getId()),
                    s.getTaskTitle() + " is yours now."));
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
            actions.setOpaque(false);
            actions.add(decline);
            actions.add(accept);

            User fromUser = members.get(s.getFromUser());
            add(centeredVertically(fromUser != null ? new AvatarLabel(fromUser, 34) : new AvatarLabel(from, 34)),
                    BorderLayout.WEST);
            add(leftCentered(text), BorderLayout.CENTER);
            add(centeredVertically(actions), BorderLayout.EAST);
            if (divider) {
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, DIVIDER), new EmptyBorder(9, 0, 9, 0)));
            }
            setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
        }
    }

    /** The amber-tinted bar for swaps waiting for you (same look as on Expenses). */
    private static class AttentionCard extends JPanel {
        AttentionCard() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(2, 18, 2, 18));
        }

        @Override
        public Insets getInsets() {
            Insets in = super.getInsets();
            return new Insets(in.top + 12, in.left, in.bottom, in.right); // gap above the bar
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape card = new RoundRectangle2D.Float(0.5f, 12.5f, getWidth() - 1f, getHeight() - 13f, 22, 22);
            g2.setColor(Theme.BANNER);
            g2.fill(card);
            g2.setColor(new Color(201, 164, 110, 120));
            g2.draw(card);
            g2.dispose();
        }
    }
}
