package com.settlr.ui.screens;

import com.settlr.model.SwapRequest;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import com.settlr.model.User;
import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.components.CheckCircle;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.StatusBadge;
import com.settlr.ui.theme.Theme;
import com.settlr.util.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.settlr.ui.components.Ui.*;

/** One task: tick circle, (avatar), title + points, details, when, and Swap. Click it for details. */
class TaskRow extends JPanel {

    /** What the row's buttons do; the screen decides (it opens the popups and reloads). */
    interface Actions {
        /** The tick circle was clicked. */
        void complete(TaskAssignment a);
        /** Swap was clicked. */
        void swap(TaskAssignment a);
        /** The row itself was clicked. */
        void details(TaskAssignment a);
    }

    private static final Color DIVIDER = new Color(232, 217, 205, 40);
    private static final String DOT = TasksScreen.DOT;

    private final boolean divider;
    private final boolean otherWeek;
    private boolean hover;

    /**
     * @param everyone  true on "Everyone": show whose turn it is
     * @param otherWeek true when not looking at this week: show times instead of days
     * @param canTick   whether the logged-in user may tick it off
     */
    TaskRow(TaskAssignment a, TaskWeek data, boolean everyone, boolean otherWeek, boolean canTick,
            boolean divider, Actions actions) {
        super(new BorderLayout(12, 0));
        this.divider = divider;
        this.otherWeek = otherWeek;
        setOpaque(false);
        setBorder(new EmptyBorder(8, 0, 8, 0));
        int me = Session.getInstance().getCurrentUser().getId();
        Task task = data.tasks.get(a.getTaskId());
        boolean mine = a.getAssigneeId() == me;
        boolean pastDue = a.getStatus() == TaskStatus.PENDING && a.getDueAt().isBefore(LocalDateTime.now());

        // left: tick + (avatar)
        CheckCircle check = new CheckCircle(a.getStatus(), pastDue, canTick, () -> actions.complete(a));
        JPanel lefty = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        lefty.setOpaque(false);
        lefty.add(check);
        if (everyone) {
            lefty.add(Box.createHorizontalStrut(10));
            User u = data.members.get(a.getAssigneeId());
            lefty.add(u != null ? new AvatarLabel(u, 26) : new AvatarLabel(a.getAssigneeName(), 26));
        }

        // middle: title, points, details
        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        title.setOpaque(false);
        boolean finished = TasksScreen.isDone(a);
        String t = a.getTaskTitle();
        title.add(label(finished ? "<html><s>" + t + "</s></html>" : t, Font.BOLD, 14,
                finished ? Theme.MUTED_TEXT : Theme.TEXT));
        title.add(Box.createHorizontalStrut(8));
        title.add(new StatusBadge(TasksScreen.points(a.getTaskPoints()), StatusBadge.Kind.PENDING));

        List<String> bits = new ArrayList<>();
        if (everyone) {
            bits.add(mine ? "You" : a.getAssigneeName());
        }
        if (TasksScreen.isPlanned(a)) {
            bits.add("Coming up");
        }
        if (task != null) {
            bits.add(TasksScreen.repeats(task) + (TasksScreen.rotates(task) ? ", turns rotate" : ""));
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
        SwapRequest asked = data.myPendingSwaps.get(a.getId());
        if (asked != null) {
            right.add(new StatusBadge("Asked " + asked.getToUserName(), StatusBadge.Kind.PENDING));
        } else if (mine && !TasksScreen.isPlanned(a) && a.getStatus() == TaskStatus.PENDING && !pastDue) {
            RoundedButton swap = TasksScreen.smallButton("Swap");
            swap.addActionListener(e -> actions.swap(a));
            right.add(swap);
        }

        add(ListRow.centeredVertically(lefty), BorderLayout.WEST);
        add(ListRow.leftCentered(text), BorderLayout.CENTER);
        add(ListRow.centeredVertically(right), BorderLayout.EAST);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));

        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("Details");
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { actions.details(a); }
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
        });
    }

    /** Done, Done late, Missed, or when it's due (red when past due). */
    private JLabel statusLabel(TaskAssignment a, boolean pastDue) {
        switch (a.getStatus()) {
            case DONE:      return label("Done", Font.PLAIN, 12, Theme.MINT);
            case DONE_LATE: return label("Done late", Font.PLAIN, 12, Theme.AMBER);
            case MISSED:    return label("Missed", Font.PLAIN, 12, Theme.DANGER);
            default:
                if (pastDue) {
                    LocalDate d = a.getDueAt().toLocalDate();
                    return label(d.equals(LocalDate.now()) ? TasksScreen.time(a.getDueAt()) : TasksScreen.day(d), Font.PLAIN, 12,
                            Theme.DANGER);
                }
                LocalDate d = a.getDueAt().toLocalDate();
                boolean soon = d.equals(LocalDate.now()) || d.equals(LocalDate.now().plusDays(1)) || otherWeek;
                return label(soon ? TasksScreen.time(a.getDueAt()) : TasksScreen.when(a.getDueAt()), Font.PLAIN, 12, Theme.TEXT);
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
