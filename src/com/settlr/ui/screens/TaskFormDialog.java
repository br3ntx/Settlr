package com.settlr.ui.screens;

import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.model.Household;
import com.settlr.model.OneTimeTask;
import com.settlr.model.Recurrence;
import com.settlr.model.RecurringTask;
import com.settlr.model.Task;
import com.settlr.model.User;
import com.settlr.service.HouseholdService;
import com.settlr.service.TaskService;
import com.settlr.ui.ModalPopup;
import com.settlr.ui.components.DatePickerField;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.MemberChip;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.SegmentedControl;
import com.settlr.ui.components.StyledTextField;
import com.settlr.ui.theme.Theme;
import com.settlr.util.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * "New task" popup (Figma: TASKS pop-up). Title, how often it repeats, the deadline (or first
 * due date), whether turns rotate, and who it's assigned to (or who goes first).
 * Save creates a OneTimeTask or a RecurringTask through TaskService, which also creates its
 * first assignment; later turns are made as each one is done and by the startup catch-up.
 *
 * Open it with:
 *     TaskFormDialog.open(this, task -> reload());
 * The second argument receives the saved task, so the screen can refresh.
 *
 * Not in the Figma yet, so not in this form: points (every task is worth DEFAULT_POINTS)
 * and a description. TaskService already supports both.
 */
public class TaskFormDialog extends ModalPopup {

    /** Points for a task made here; the Figma form has no difficulty picker yet. */
    public static final int DEFAULT_POINTS = 1;
    /** A deadline is a whole day, so the task is due at the end of that day. */
    private static final LocalTime DUE_TIME = LocalTime.of(23, 59);

    private static final int FORM_WIDTH = 380;
    private static final int FIELD_HEIGHT = 36;

    /** The "Repeats" options, in order. Index 0 is a one-time task; the rest match REPEATS. */
    private static final String[] REPEAT_OPTIONS = {"Once", "Daily", "Weekly", "Monthly"};
    private static final Recurrence[] REPEATS = {null, Recurrence.DAILY, Recurrence.WEEKLY, Recurrence.MONTHLY};
    /** The "Turns" options: index 1 passes the task to the next member each time. */
    private static final String[] TURN_OPTIONS = {"Same person", "Take turns"};

    private final TaskService taskService = new TaskService();
    private final Consumer<Task> onSaved;
    private final List<User> members;

    private final StyledTextField titleField = new StyledTextField("e.g. Wash the dishes");
    private final DatePickerField deadlineField = new DatePickerField(null); // empty until picked
    private final List<MemberChip> chips = new ArrayList<>();
    private final JLabel errorLabel = new JLabel(" ");

    private final SegmentedControl repeatsControl = new SegmentedControl(REPEAT_OPTIONS, i -> updateForRepeat());
    private final SegmentedControl turnsControl = new SegmentedControl(TURN_OPTIONS, i -> updateForRepeat());
    private final JLabel repeatHint = new JLabel(" ");
    private final JLabel deadlineLabel = label("Deadline");
    private final JLabel assignLabel = label("Assign to");
    private final JPanel turnsBlock = new JPanel(); // hidden for one-time tasks

    /** Opens the popup over the window that contains anyComponent. */
    public static void open(Component anyComponent, Consumer<Task> onSaved) {
        List<User> members;
        try {
            Household household = Session.getInstance().getCurrentHousehold();
            if (household == null) {
                new ConfirmDialog("Join a household first", "Tasks and expenses belong to a household. "
                        + "Create one or join with an invite code from a roommate.").okOnly("OK").showOver(anyComponent);
                return;
            }
            members = new HouseholdService().getMembers();
        } catch (SettlrException e) {
            new ConfirmDialog("Can't open this right now", e instanceof DataAccessException
                    ? "Can't load your household members. Is MySQL running?"
                    : "Can't load your household members: " + e.getMessage()).okOnly("OK").showOver(anyComponent);
            return;
        }
        TaskFormDialog popup = new TaskFormDialog(members, onSaved);
        popup.showOver(anyComponent);
        SwingUtilities.invokeLater(popup.titleField::requestFocusInWindow);
    }

    private TaskFormDialog(List<User> members, Consumer<Task> onSaved) {
        this.members = members;
        this.onSaved = onSaved;
        StyledTextField.usePillStyle(titleField);
        add(buildCard());
        titleField.addActionListener(e -> handleSave()); // Enter saves
    }

    // ---------- layout ----------

    private GlassCard buildCard() {
        GlassCard card = new GlassCard(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 28, 24, 28));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("New task");
        title.setFont(Theme.font(Font.BOLD, 26));
        title.setForeground(Theme.TEXT);
        header.add(title, BorderLayout.WEST);
        header.add(createCloseButton(), BorderLayout.EAST);
        row(card, header, 0, 34, FORM_WIDTH);

        row(card, label("Task"), 10, 16, FORM_WIDTH);
        row(card, titleField, 4, FIELD_HEIGHT, FORM_WIDTH);

        row(card, label("Repeats"), 12, 16, FORM_WIDTH);
        row(card, leftAligned(repeatsControl), 4, 32, FORM_WIDTH);
        repeatHint.setFont(Theme.font(Font.PLAIN, 11));
        repeatHint.setForeground(Theme.MUTED_TEXT);
        repeatHint.setBorder(new EmptyBorder(0, 8, 0, 0));
        row(card, repeatHint, 4, 14, FORM_WIDTH);

        row(card, deadlineLabel, 10, 16, FORM_WIDTH);
        JPanel deadlineRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        deadlineRow.setOpaque(false);
        deadlineField.setPreferredSize(new Dimension(FORM_WIDTH / 2, FIELD_HEIGHT));
        deadlineRow.add(deadlineField);
        row(card, deadlineRow, 4, FIELD_HEIGHT, FORM_WIDTH);

        // Only for repeating tasks: keep it with one person, or pass it around the household
        turnsBlock.setLayout(new BoxLayout(turnsBlock, BoxLayout.Y_AXIS));
        turnsBlock.setOpaque(false);
        row(turnsBlock, label("Turns"), 14, 16, FORM_WIDTH);
        row(turnsBlock, leftAligned(turnsControl), 4, 32, FORM_WIDTH);
        row(card, turnsBlock, 0, 66, FORM_WIDTH);

        row(card, assignLabel, 14, 16, FORM_WIDTH);
        JPanel chipPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        chipPanel.setOpaque(false);
        ButtonGroup oneAssignee = new ButtonGroup(); // a task goes to exactly one person
        int me = Session.getInstance().getCurrentUser().getId();
        for (User u : members) {
            String name = u.getId() == me ? u.getDisplayName() + " (you)" : u.getDisplayName();
            MemberChip chip = new MemberChip(u.getId(), name, u.getId() == me); // you, by default
            oneAssignee.add(chip);
            chips.add(chip);
            chipPanel.add(chip);
        }
        row(card, chipPanel, 0, chipRowsHeight(), FORM_WIDTH + 12);

        errorLabel.setFont(Theme.font(Font.PLAIN, 12));
        errorLabel.setForeground(Theme.DANGER);
        errorLabel.setBorder(new EmptyBorder(0, 8, 0, 0));
        row(card, errorLabel, 2, 16, FORM_WIDTH);

        RoundedButton save = new RoundedButton("Save");
        save.setArc(RoundedButton.PILL);
        save.setFont(Theme.font(Font.BOLD, 14));
        save.addActionListener(e -> handleSave());
        row(card, save, 6, 40, FORM_WIDTH);
        updateForRepeat();
        return card;
    }

    /** Wraps a fixed-size control so it sits on the left of its row instead of stretching. */
    private static JPanel leftAligned(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.add(c);
        return p;
    }

    /** Updates the labels and hides "Turns" to match the chosen "Repeats" option. */
    private void updateForRepeat() {
        Recurrence how = REPEATS[repeatsControl.getSelected()];
        boolean rotates = how != null && turnsControl.getSelected() == 1;
        deadlineLabel.setText(how == null ? "Deadline" : "First due");
        assignLabel.setText(rotates ? "Who goes first" : "Assign to");
        repeatHint.setText(repeatHint(how, rotates));
        turnsBlock.setVisible(how != null);
        revalidate();
        repaint();
    }

    /** The small line under "Repeats" that says what the choice means. */
    static String repeatHint(Recurrence how, boolean rotates) {
        if (how == null) {
            return "Done once, by the deadline.";
        }
        String every;
        switch (how) {
            case DAILY: every = "Every day"; break;
            case WEEKLY: every = "Every week on the same day"; break;
            default: every = "Every month on the same day (1 to 28)"; break;
        }
        return every + (rotates ? ", taking turns." : ", same person each time.");
    }

    /** Height for the member chips, which wrap onto more lines when there are many. */
    private int chipRowsHeight() {
        int rows = 1, x = 0;
        for (MemberChip chip : chips) {
            int w = chip.getPreferredSize().width + 6;
            if (x > 0 && x + w > FORM_WIDTH + 6) {
                rows++;
                x = 0;
            }
            x += w;
        }
        return rows * (34 + 6) + 6;
    }

    private void row(JPanel card, JComponent c, int spaceAbove, int height, int width) {
        if (spaceAbove > 0) {
            card.add(Box.createVerticalStrut(spaceAbove));
        }
        Dimension d = new Dimension(width, height);
        c.setPreferredSize(d);
        c.setMinimumSize(d);
        c.setMaximumSize(d);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(c);
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.BOLD, 12));
        l.setForeground(Theme.PRIMARY);
        l.setBorder(new EmptyBorder(0, 8, 0, 0));
        return l;
    }

    // ---------- save ----------

    private Integer selectedAssignee() {
        for (MemberChip chip : chips) {
            if (chip.isSelected()) {
                return chip.getUserId();
            }
        }
        return null;
    }

    private void handleSave() {
        errorLabel.setText(" ");
        if (titleField.getText().isBlank()) {
            showError("Please enter what the task is.");
            titleField.requestFocusInWindow();
            return;
        }
        LocalDate deadline = deadlineField.getDate();
        if (deadline == null) {
            showError("Please pick a deadline.");
            return;
        }
        if (deadline.isBefore(LocalDate.now())) {
            showError("The deadline can't be in the past.");
            return;
        }
        Integer assignee = selectedAssignee();
        if (assignee == null) {
            showError("Please choose who the task is for.");
            return;
        }
        Recurrence how = REPEATS[repeatsControl.getSelected()];
        if (how == Recurrence.MONTHLY && deadline.getDayOfMonth() > 28) {
            showError("Monthly tasks must start on day 1 to 28, so every month has that day.");
            return;
        }
        try {
            Task task = buildTask(titleField.getText(), deadline.atTime(DUE_TIME), assignee,
                    how, how != null && turnsControl.getSelected() == 1);
            taskService.createTask(task); // also creates the first assignment
            close();
            if (onSaved != null) {
                onSaved.accept(task);
            }
        } catch (IllegalArgumentException e) {
            showError(e.getMessage()); // a model rule, e.g. "Task title can be at most 80 characters."
        } catch (DataAccessException e) {
            showError("Can't save right now. Is MySQL running?");
            e.printStackTrace();
        } catch (SettlrException e) {
            showError(e.getMessage());
        }
    }

    /**
     * The task the form describes: a OneTimeTask when how is null, otherwise a RecurringTask that
     * repeats that often and, if rotates is true, passes to the next member after each turn.
     */
    static Task buildTask(String title, LocalDateTime firstDue, int assignee, Recurrence how, boolean rotates) {
        if (how == null) {
            return new OneTimeTask(title, null, DEFAULT_POINTS, firstDue, assignee);
        }
        return new RecurringTask(title, null, DEFAULT_POINTS, firstDue, assignee, how, rotates);
    }

    private void showError(String message) {
        errorLabel.setText(message);
    }
}
