package com.settlr.ui.screens;

import com.settlr.dao.MembershipDao;
import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.model.Expense;
import com.settlr.model.ExpenseCategory;
import com.settlr.model.ExpenseShare;
import com.settlr.model.Household;
import com.settlr.model.User;
import com.settlr.service.ExpenseService;
import com.settlr.split.EqualSplit;
import com.settlr.ui.components.DatePickerField;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.MemberChip;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.StyledComboBox;
import com.settlr.ui.components.StyledTextField;
import com.settlr.ui.theme.Theme;
import com.settlr.util.MoneyUtil;
import com.settlr.util.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * "New expense" popup (Figma: EXPENSES (POP UP)). Opens on top of the current
 * screen and dims it. Save splits the amount equally among the chosen members.
 *
 * Open it with:
 *     ExpenseFormDialog.open(this, () -> reload());
 * The second argument runs after a successful save, so the screen can refresh.
 *
 * It is drawn inside the main window (on the window's modal layer), not as a
 * separate window, so the rounded corners and the dimmed background work on every PC,
 * and dropdown lists and the calendar still open on top of it.
 */
public class ExpenseFormDialog extends JPanel {

    private static final int FORM_WIDTH = 330;  // width of everything inside the card
    private static final int HALF = (FORM_WIDTH - 12) / 2;
    private static final int FIELD_HEIGHT = 34;

    private final JLayeredPane layers;
    private final ComponentAdapter resizer = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent e) { fitToWindow(); }
    };
    private final Runnable onSaved;
    private final ExpenseService expenseService = new ExpenseService();

    private final StyledTextField titleField = pillField("e.g. Wifi");
    private final StyledTextField amountField = pillField("0.00");
    private final StyledComboBox<ExpenseCategory> categoryBox =
            new StyledComboBox<>(Arrays.asList(ExpenseCategory.values()), ExpenseCategory::name);
    private final StyledComboBox<User> paidByBox;
    private final DatePickerField dueDateField = new DatePickerField(LocalDate.now());
    private final List<MemberChip> chips = new ArrayList<>();
    private final List<User> members;
    private final JLabel previewLabel = new JLabel();
    private final JLabel errorLabel = new JLabel(" ");

    /** Opens the popup over the window that contains anyComponent. */
    public static void open(Component anyComponent, Runnable onSaved) {
        JRootPane root = SwingUtilities.getRootPane(anyComponent);
        if (root == null) {
            throw new IllegalStateException("Open the popup from a component that is inside a window.");
        }
        List<User> members;
        try {
            Household household = Session.getInstance().getCurrentHousehold();
            if (household == null) {
                JOptionPane.showMessageDialog(anyComponent, "Join a household first.");
                return;
            }
            members = new MembershipDao().findActiveMembers(household.getId());
        } catch (DataAccessException e) {
            JOptionPane.showMessageDialog(anyComponent, "Can't load your household members: " + e.getMessage());
            return;
        }
        ExpenseFormDialog popup = new ExpenseFormDialog(root.getLayeredPane(), members, onSaved);
        popup.layers.add(popup, JLayeredPane.MODAL_LAYER); // above the screens, below dropdown lists
        popup.layers.addComponentListener(popup.resizer);
        popup.fitToWindow();
        SwingUtilities.invokeLater(popup.titleField::requestFocusInWindow);
    }

    private ExpenseFormDialog(JLayeredPane layers, List<User> members, Runnable onSaved) {
        this.layers = layers;
        this.onSaved = onSaved;
        this.members = members;
        this.paidByBox = new StyledComboBox<>(members, User::getDisplayName);

        setOpaque(false); // paintComponent draws the dim layer
        setLayout(new GridBagLayout()); // centers the card
        setFocusCycleRoot(true); // Tab stays inside the popup
        // Swallow clicks on the dimmed area so nothing behind the popup reacts
        addMouseListener(new MouseAdapter() { });
        addMouseMotionListener(new MouseAdapter() { });

        add(buildCard());
        selectCurrentUserAsPayer();
        wireUpLivePreview();
        updatePreview();

        // Esc closes, like any popup
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getActionMap().put("close", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { close(); }
        });
    }

    private void fitToWindow() {
        setBounds(0, 0, layers.getWidth(), layers.getHeight());
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Theme.OVERLAY);
        g.fillRect(0, 0, getWidth(), getHeight());
    }

    // ---------- layout ----------

    private GlassCard buildCard() {
        GlassCard card = new GlassCard(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 26, 22, 26));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel title = new JLabel("New expense");
        title.setFont(Theme.font(Font.BOLD, 22));
        title.setForeground(Theme.TEXT);
        header.add(title, BorderLayout.WEST);
        header.add(closeButton(), BorderLayout.EAST);
        row(card, header, 0, 30);

        row(card, label("Title"), 10, 16);
        row(card, titleField, 4, FIELD_HEIGHT);

        row(card, pair(label("Category"), label("Paid by")), 10, 16);
        row(card, pair(categoryBox, paidByBox), 4, FIELD_HEIGHT);

        row(card, pair(label("Amount"), label("Due date")), 10, 16);
        row(card, pair(amountField, dueDateField), 4, FIELD_HEIGHT);

        row(card, label("Split with"), 10, 16);
        JPanel chipPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        chipPanel.setOpaque(false);
        int currentUserId = Session.getInstance().getCurrentUser().getId();
        for (User u : members) {
            String name = u.getId() == currentUserId ? u.getDisplayName() + " (you)" : u.getDisplayName();
            MemberChip chip = new MemberChip(u.getId(), name, true); // everyone is in by default
            chips.add(chip);
            chipPanel.add(chip);
        }
        row(card, chipPanel, 0, chipRowsHeight());

        previewLabel.setFont(Theme.font(Font.PLAIN, 11));
        previewLabel.setForeground(Theme.MUTED_TEXT);
        previewLabel.setBorder(new EmptyBorder(0, 8, 0, 0));
        row(card, previewLabel, 2, 16);

        errorLabel.setFont(Theme.font(Font.PLAIN, 12));
        errorLabel.setForeground(Theme.DANGER);
        errorLabel.setBorder(new EmptyBorder(0, 8, 0, 0));
        row(card, errorLabel, 2, 16);

        RoundedButton save = new RoundedButton("Save");
        save.setArc(RoundedButton.PILL);
        save.setFont(Theme.font(Font.BOLD, 13));
        save.addActionListener(e -> handleSave());
        row(card, save, 6, FIELD_HEIGHT);
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

    /** Two things side by side, each half the width. */
    private JPanel pair(JComponent left, JComponent right) {
        JPanel p = new JPanel(new GridLayout(1, 2, 12, 0));
        p.setOpaque(false);
        p.add(left);
        p.add(right);
        return p;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.BOLD, 12));
        l.setForeground(Theme.PRIMARY);
        l.setBorder(new EmptyBorder(0, 8, 0, 0));
        return l;
    }

    private static StyledTextField pillField(String placeholder) {
        StyledTextField f = new StyledTextField(placeholder);
        StyledTextField.usePillStyle(f);
        f.setFont(Theme.font(Font.PLAIN, 12));
        return f;
    }

    /** An X drawn with two lines (the X character is missing from some fonts). */
    private JButton closeButton() {
        JButton x = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getForeground());
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2, cy = getHeight() / 2, r = 6;
                g2.drawLine(cx - r, cy - r, cx + r, cy + r);
                g2.drawLine(cx - r, cy + r, cx + r, cy - r);
                g2.dispose();
            }
        };
        x.setPreferredSize(new Dimension(30, 30));
        x.setForeground(Theme.MUTED_TEXT);
        x.setContentAreaFilled(false);
        x.setBorderPainted(false);
        x.setFocusPainted(false);
        x.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        x.setToolTipText("Close");
        x.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { x.setForeground(Theme.TEXT); }
            @Override public void mouseExited(MouseEvent e) { x.setForeground(Theme.MUTED_TEXT); }
        });
        x.addActionListener(e -> close());
        return x;
    }

    private void selectCurrentUserAsPayer() {
        int me = Session.getInstance().getCurrentUser().getId();
        for (User u : members) {
            if (u.getId() == me) {
                paidByBox.setSelectedItem(u);
            }
        }
    }

    // ---------- live share preview ----------

    private void wireUpLivePreview() {
        DocumentListener onEdit = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { edited(); }
            @Override public void removeUpdate(DocumentEvent e) { edited(); }
            @Override public void changedUpdate(DocumentEvent e) { edited(); }
        };
        titleField.getDocument().addDocumentListener(onEdit);
        amountField.getDocument().addDocumentListener(onEdit);
        paidByBox.addActionListener(e -> updatePreview());
        for (MemberChip chip : chips) {
            chip.addActionListener(e -> updatePreview());
        }
    }

    /** Typing clears an old error and refreshes the share preview. */
    private void edited() {
        errorLabel.setText(" ");
        updatePreview();
    }

    /** "\u20B1312.50 each (4 people)", worked out with the same EqualSplit that Save uses. */
    private void updatePreview() {
        List<Integer> ids = selectedMemberIds();
        BigDecimal amount = parseAmountOrNull();
        if (ids.isEmpty()) {
            previewLabel.setText("Pick at least one person to split with.");
            return;
        }
        if (amount == null) {
            previewLabel.setText("Enter an amount to see each share.");
            return;
        }
        User payer = paidByBox.getSelected();
        List<ExpenseShare> shares;
        try {
            shares = new EqualSplit().split(amount, payer.getId(), ids);
        } catch (IllegalArgumentException e) {
            previewLabel.setText(e.getMessage());
            return;
        }
        BigDecimal base = shares.get(shares.size() - 1).getAmountOwed();
        BigDecimal most = shares.stream().map(ExpenseShare::getAmountOwed).max(BigDecimal::compareTo).orElse(base);
        BigDecimal least = shares.stream().map(ExpenseShare::getAmountOwed).min(BigDecimal::compareTo).orElse(base);
        String people = ids.size() == 1 ? "1 person" : ids.size() + " people";
        if (most.compareTo(least) == 0) {
            previewLabel.setText(MoneyUtil.format(least) + " each (" + people + ")");
        } else {
            previewLabel.setText(MoneyUtil.format(least) + " each, one pays " + MoneyUtil.format(most)
                    + " for the extra centavos (" + people + ")");
        }
    }

    private List<Integer> selectedMemberIds() {
        List<Integer> ids = new ArrayList<>();
        for (MemberChip chip : chips) {
            if (chip.isSelected()) {
                ids.add(chip.getUserId());
            }
        }
        return ids;
    }

    /** Accepts "1250", "1,250.50" or "\u20B11,250.50"; null if it isn't a positive amount. */
    private BigDecimal parseAmountOrNull() {
        String text = amountField.getText().replace(",", "").replace(MoneyUtil.PESO, "").trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text);
            return value.signum() > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---------- save / close ----------

    private void handleSave() {
        errorLabel.setText(" ");
        if (titleField.getText().isBlank()) {
            showError("Please enter a title.");
            titleField.requestFocusInWindow();
            return;
        }
        BigDecimal amount = parseAmountOrNull();
        if (amount == null) {
            showError("Enter an amount more than \u20B10, like 1250.00.");
            amountField.requestFocusInWindow();
            return;
        }
        try {
            Session s = Session.getInstance();
            Expense expense = new Expense(0, s.getCurrentHousehold().getId(), null,
                    titleField.getText(), categoryBox.getSelected(), amount,
                    paidByBox.getSelected().getId(), dueDateField.getDate(),
                    s.getCurrentUser().getId(), LocalDateTime.now());
            expenseService.addExpense(expense, selectedMemberIds(), new EqualSplit());
            close();
            if (onSaved != null) {
                onSaved.run();
            }
        } catch (IllegalArgumentException e) {
            showError(e.getMessage()); // a model rule, e.g. "Expense title can be at most 80 characters."
        } catch (DataAccessException e) {
            showError("Can't save right now. Is MySQL running?");
            e.printStackTrace();
        } catch (SettlrException e) {
            showError(e.getMessage()); // e.g. "Please pick at least one member to split with."
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
    }

    /** Removes the popup from the window. */
    public void close() {
        layers.removeComponentListener(resizer);
        layers.remove(this);
        layers.revalidate();
        layers.repaint();
    }
}
