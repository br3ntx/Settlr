package com.settlr.ui.screens;

import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.model.Household;
import com.settlr.model.OneTimeTask;
import com.settlr.model.Task;
import com.settlr.model.User;
import com.settlr.service.HouseholdService;
import com.settlr.service.TaskService;
import com.settlr.ui.ModalPopup;
import com.settlr.ui.components.DatePickerField;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.MemberChip;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.StyledTextField;
import com.settlr.ui.theme.Theme;
import com.settlr.util.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * "New task" popup (Figma: TASKS pop-up). Title, deadline, and who it's assigned to.
 * Save creates a one-time task through TaskService (which also creates its first assignment).
 *
 * Open it with:
 *     TaskFormDialog.open(this, task -> reload());
 * The second argument receives the saved task, so the screen can refresh.
 *
 * Not in the Figma yet, so not in this form: points (every task is worth DEFAULT_POINTS),
 * a description, and repeating tasks. TaskService already supports all three.
 */
public class TaskFormDialog extends ModalPopup {

    /** Points for a task made here; the Figma form has no difficulty picker yet. */
    public static final int DEFAULT_POINTS = 1;
    /** A deadline is a whole day, so the task is due at the end of that day. */
    private static final LocalTime DUE_TIME = LocalTime.of(23, 59);

    private static final int FORM_WIDTH = 380;
    private static final int FIELD_HEIGHT = 36;

    private final TaskService taskService = new TaskService();
    private final Consumer<Task> onSaved;
    private final List<User> members;

    private final StyledTextField titleField = new StyledTextField("e.g. Wash the dishes");
    private final DatePickerField deadlineField = new DatePickerField(null); // empty until picked
    private final List<MemberChip> chips = new ArrayList<>();
    private final JLabel errorLabel = new JLabel(" ");

    /** Opens the popup over the window that contains anyComponent. */
    public static void open(Component anyComponent, Consumer<Task> onSaved) {
        List<User> members;
        try {
            Household household = Session.getInstance().getCurrentHousehold();
            if (household == null) {
                JOptionPane.showMessageDialog(anyComponent, "Join a household first.");
                return;
            }
            members = new HouseholdService().getMembers();
        } catch (SettlrException e) {
            JOptionPane.showMessageDialog(anyComponent, "Can't load your household members: " + e.getMessage());
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

        row(card, label("Deadline"), 12, 16, FORM_WIDTH);
        JPanel deadlineRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        deadlineRow.setOpaque(false);
        deadlineField.setPreferredSize(new Dimension(FORM_WIDTH / 2, FIELD_HEIGHT));
        deadlineRow.add(deadlineField);
        row(card, deadlineRow, 4, FIELD_HEIGHT, FORM_WIDTH);

        row(card, label("Assign to"), 14, 16, FORM_WIDTH);
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
        return card;
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
        try {
            Task task = new OneTimeTask(titleField.getText(), null, DEFAULT_POINTS,
                    deadline.atTime(DUE_TIME), assignee);
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

    private void showError(String message) {
        errorLabel.setText(message);
    }
}
