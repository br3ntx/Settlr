package com.settlr.ui.screens;

import com.settlr.exception.SettlrException;
import com.settlr.model.SwapRequest;
import com.settlr.model.Task;
import com.settlr.model.TaskAssignment;
import com.settlr.model.TaskStatus;
import com.settlr.service.TaskService;
import com.settlr.ui.ModalPopup;
import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.StatusBadge;
import com.settlr.ui.theme.Theme;
import com.settlr.util.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What you see after clicking a task on the Tasks page: its description, when it's due, how often
 * it repeats, whose turn it is and (for rotating tasks) who's next. From here you can mark it done,
 * ask someone to swap, or archive it (if you made it, or you're an Admin).
 *
 *     TaskDetailDialog.open(this, assignment, task, pendingSwapOrNull, listener);
 */
public class TaskDetailDialog extends ModalPopup {

    /** What the Tasks page does when a button here is clicked. */
    public interface Listener {
        void completed();
        void askSwap();
        void archived(String title);
    }

    private static final int FORM_WIDTH = 400;
    private static final int TURNS_SHOWN = 5;
    private static final DateTimeFormatter DONE_AT = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH);

    private final TaskService taskService = new TaskService();

    public static void open(Component over, TaskAssignment a, Task task, SwapRequest pendingSwap, Listener listener) {
        new TaskDetailDialog(a, task, pendingSwap, listener).showOver(over);
    }

    private TaskDetailDialog(TaskAssignment a, Task task, SwapRequest pendingSwap, Listener listener) {
        int me = Session.getInstance().getCurrentUser().getId();
        boolean mine = a.getAssigneeId() == me;
        boolean pastDue = a.getDueAt().isBefore(LocalDateTime.now());
        boolean canTick, canModify;
        List<TaskService.Turn> turns = new ArrayList<>();
        try {
            canTick = taskService.canComplete(a);
            canModify = taskService.canModify(task);
            if (TasksScreen.rotates(task) && a.getStatus() == TaskStatus.PENDING) {
                turns = taskService.upcomingTurns(a, TURNS_SHOWN);
            }
        } catch (SettlrException e) {
            canTick = false;
            canModify = false;
        }
        boolean planned = TasksScreen.isPlanned(a);
        boolean canSwap = mine && !planned && a.getStatus() == TaskStatus.PENDING && !pastDue && pendingSwap == null;

        GlassCard card = new GlassCard(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 26, 20, 26));

        // title + points, close
        JPanel titleLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleLine.setOpaque(false);
        titleLine.add(TasksScreen.label(a.getTaskTitle(), Font.BOLD, 21, Theme.TEXT));
        titleLine.add(Box.createHorizontalStrut(10));
        titleLine.add(new StatusBadge(TasksScreen.points(a.getTaskPoints()), StatusBadge.Kind.PENDING));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(titleLine, BorderLayout.CENTER);
        JPanel closeHolder = new JPanel(new BorderLayout());
        closeHolder.setOpaque(false);
        closeHolder.add(createCloseButton(), BorderLayout.NORTH);
        header.add(closeHolder, BorderLayout.EAST);
        row(card, header, 0, 32);
        if (task != null && task.getDescription() != null && !task.getDescription().isBlank()) {
            JLabel desc = TasksScreen.label("<html>" + escape(task.getDescription()) + "</html>", Font.PLAIN, 12,
                    Theme.MUTED_TEXT);
            row(card, desc, 4, Math.min(48, desc.getPreferredSize().height
                    * Math.max(1, desc.getPreferredSize().width / FORM_WIDTH + 1)));
        }

        // facts
        JPanel facts = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Shape r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 14, 14);
                g2.setColor(new Color(255, 255, 255, 10));
                g2.fill(r);
                g2.setColor(new Color(255, 255, 255, 55));
                g2.draw(r);
                g2.dispose();
            }
        };
        facts.setOpaque(false);
        facts.setLayout(new BoxLayout(facts, BoxLayout.Y_AXIS));
        facts.setBorder(new EmptyBorder(6, 16, 6, 16));
        facts.add(fact("Due", TasksScreen.when(a.getDueAt()), pastDue && a.getStatus() == TaskStatus.PENDING
                ? Theme.DANGER : Theme.TEXT));
        facts.add(fact("Repeats", task == null ? "\u2014" : repeatsLong(task), Theme.TEXT));
        facts.add(fact("Whose turn", mine ? "You" : a.getAssigneeName(), Theme.TEXT));
        if (a.getStatus() != TaskStatus.PENDING) {
            String status;
            Color color;
            switch (a.getStatus()) {
                case DONE:
                    status = "Done" + (a.getCompletedAt() == null ? "" : " " + DONE_AT.format(a.getCompletedAt()));
                    color = Theme.MINT;
                    break;
                case DONE_LATE:
                    status = "Done late" + (a.getCompletedAt() == null ? "" : " " + DONE_AT.format(a.getCompletedAt()));
                    color = Theme.AMBER;
                    break;
                default:
                    status = "Missed";
                    color = Theme.DANGER;
            }
            facts.add(fact("Status", status, color));
        }
        row(card, facts, 16, facts.getPreferredSize().height);

        // who's next (rotating tasks)
        if (turns.size() > 1) {
            row(card, TasksScreen.label("Turns rotate", Font.BOLD, 12, Theme.PRIMARY), 16, 16);
            JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            strip.setOpaque(false);
            for (int i = 0; i < turns.size(); i++) {
                if (i > 0) {
                    JLabel arrow = TasksScreen.label("\u203A", Font.BOLD, 14, Theme.MUTED_TEXT);
                    arrow.setBorder(new EmptyBorder(8, 6, 0, 6));
                    strip.add(arrow);
                }
                strip.add(turn(turns.get(i), me, i == 0));
            }
            row(card, strip, 8, strip.getPreferredSize().height);
            row(card, TasksScreen.label("Turns after this one are a preview; swaps can change them.",
                    Font.PLAIN, 11, Theme.MUTED_TEXT), 6, 14);
        }

        if (planned) {
            row(card, TasksScreen.label("Coming up. It can be ticked off or swapped once it's the next turn.",
                    Font.PLAIN, 12, Theme.MUTED_TEXT), 14, 16);
        } else if (pendingSwap != null) {
            row(card, TasksScreen.label("You asked " + pendingSwap.getToUserName()
                    + " to take this. It's yours until they accept.", Font.PLAIN, 12, Theme.AMBER), 14, 16);
        } else if (mine && a.getStatus() == TaskStatus.PENDING && pastDue) {
            row(card, TasksScreen.label("It's past due, so it can't be swapped. Finishing it now counts as late.",
                    Font.PLAIN, 12, Theme.DANGER), 14, 16);
        }

        // buttons
        JPanel buttons = new JPanel(new GridBagLayout());
        buttons.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 1;
        if (canSwap) {
            RoundedButton swap = RoundedButton.outline("Ask to swap");
            styleButton(swap);
            swap.addActionListener(e -> {
                close();
                listener.askSwap();
            });
            c.insets = new Insets(0, 0, 0, canTick ? 14 : 0);
            buttons.add(swap, c);
        }
        if (canTick) {
            RoundedButton done = new RoundedButton("Mark as done");
            styleButton(done);
            done.addActionListener(e -> {
                close();
                listener.completed();
            });
            c.weightx = canSwap ? 1.4 : 1;
            c.insets = new Insets(0, 0, 0, 0);
            buttons.add(done, c);
        }
        if (!canSwap && !canTick) {
            RoundedButton ok = RoundedButton.outline("Close");
            styleButton(ok);
            ok.addActionListener(e -> close());
            buttons.add(ok, c);
        }
        row(card, buttons, 20, 42);

        if (canModify && task != null) {
            JPanel links = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            links.setOpaque(false);
            JLabel archive = TasksScreen.link("Archive task", () -> {
                int answer = JOptionPane.showConfirmDialog(this, "Archive \"" + task.getTitle()
                        + "\"?\nIt won't come up again. Past turns stay in the history.", "Archive task",
                        JOptionPane.YES_NO_OPTION);
                if (answer != JOptionPane.YES_OPTION) {
                    return;
                }
                try {
                    taskService.archiveTask(task.getId());
                } catch (SettlrException ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage());
                    return;
                }
                close();
                listener.archived(task.getTitle());
            });
            archive.setForeground(Theme.DANGER);
            links.add(archive);
            row(card, links, 12, 16);
        }
        add(card);
    }

    // ---------- pieces ----------

    private static JPanel fact(String name, String value, Color valueColor) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(6, 0, 6, 0));
        p.add(TasksScreen.label(name, Font.PLAIN, 13, Theme.MUTED_TEXT), BorderLayout.WEST);
        p.add(TasksScreen.label(value, Font.BOLD, 13, valueColor), BorderLayout.EAST);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    /** Avatar with the name and day under it; the current turn gets a beige ring. */
    private static JPanel turn(TaskService.Turn t, int me, boolean current) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JPanel ring = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                if (!current) {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.PRIMARY);
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new Ellipse2D.Float(1, 1, getWidth() - 2, getHeight() - 2));
                g2.dispose();
            }
        };
        ring.setOpaque(false);
        Dimension d = new Dimension(40, 40);
        ring.setPreferredSize(d);
        ring.setMaximumSize(d);
        AvatarLabel avatar = new AvatarLabel(t.getUser(), 32);
        avatar.setBounds(4, 4, 32, 32);
        ring.add(avatar);
        boolean you = t.getUser().getId() == me;
        JLabel name = TasksScreen.label(you ? "You" : t.getUser().getDisplayName(), current ? Font.BOLD : Font.PLAIN,
                12, Theme.TEXT);
        JLabel day = TasksScreen.label(TasksScreen.day(t.getDueAt().toLocalDate()), Font.PLAIN, 10, Theme.MUTED_TEXT);
        for (JComponent c : new JComponent[] {ring, name, day}) {
            c.setAlignmentX(Component.CENTER_ALIGNMENT);
            p.add(c);
        }
        return p;
    }

    private static String repeatsLong(Task task) {
        switch (TasksScreen.repeats(task)) {
            case "Daily":   return "Every day";
            case "Weekly":  return "Every week";
            case "Monthly": return "Every month";
            default:        return "Just once";
        }
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static void styleButton(RoundedButton b) {
        b.setArc(RoundedButton.PILL);
        b.setFont(Theme.font(Font.BOLD, 14));
    }

    private void row(JPanel card, JComponent c, int spaceAbove, int height) {
        if (spaceAbove > 0) {
            card.add(Box.createVerticalStrut(spaceAbove));
        }
        Dimension d = new Dimension(FORM_WIDTH, height);
        c.setPreferredSize(d);
        c.setMinimumSize(d);
        c.setMaximumSize(d);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(c);
    }
}
