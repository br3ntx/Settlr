package com.settlr.ui.screens;

import com.settlr.exception.SettlrException;
import com.settlr.model.User;
import com.settlr.service.BalanceService;
import com.settlr.service.PaymentService;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.theme.Theme;
import com.settlr.util.MoneyUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * "Settle Up" dialog (master doc section 5): lists the unsettled shares the current user owes
 * ONE person, with checkboxes and a live total. Record Payment creates PENDING payments, so
 * the wording always says the payment still needs the receiver's confirmation. Confirming and
 * rejecting are done on ExpensesScreen. There is no amount field: a payment always covers a
 * whole share (the schema has no partial payments).
 */
public class PaymentDialog extends JDialog {

    private static final DateTimeFormatter DUE_FORMAT = DateTimeFormatter.ofPattern("MMM d");

    private final BalanceService balanceService = new BalanceService();
    private final PaymentService paymentService = new PaymentService();

    private final User creditor;
    private final List<BalanceService.ShareRow> rows = new ArrayList<>();
    private final List<JCheckBox> boxes = new ArrayList<>(); // same order as rows

    private final JPanel shareList = new JPanel();
    private final JLabel totalLabel = new JLabel(" ");
    private final JLabel errorLabel = new JLabel(" "); // a space keeps its height when empty
    private final RoundedButton recordButton = new RoundedButton("Record Payment");

    private boolean paymentRecorded = false;

    /** Opens the dialog for paying {@code creditor}; blocks until it closes; true if a payment was recorded. */
    public static boolean showFor(Window owner, User creditor) {
        PaymentDialog dialog = new PaymentDialog(owner, creditor);
        dialog.setVisible(true);
        return dialog.wasPaymentRecorded();
    }

    public PaymentDialog(Window owner, User creditor) {
        super(owner, "Settle up", ModalityType.APPLICATION_MODAL);
        if (creditor == null) {
            throw new IllegalArgumentException("Please choose who to pay.");
        }
        this.creditor = creditor;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel content = new JPanel();
        content.setBackground(Theme.SURFACE);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(32, 32, 24, 32));

        JLabel heading = new JLabel("Pay " + creditor.getDisplayName());
        heading.setFont(Theme.font(Font.BOLD, 22));
        heading.setForeground(Theme.TEXT);

        JLabel hint = new JLabel("Tick the shares you already paid. They count once "
                + creditor.getDisplayName() + " confirms.");
        hint.setFont(Theme.font(Font.PLAIN, 13));
        hint.setForeground(Theme.MUTED_TEXT);

        shareList.setOpaque(false);
        shareList.setLayout(new BoxLayout(shareList, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(shareList);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.setPreferredSize(new Dimension(460, 220));

        totalLabel.setFont(Theme.font(Font.BOLD, 18));
        totalLabel.setForeground(Theme.TEXT);
        errorLabel.setFont(Theme.font(Font.PLAIN, 13));
        errorLabel.setForeground(Theme.DANGER);

        recordButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        recordButton.addActionListener(e -> onRecord());

        JLabel cancel = new JLabel("<html><u>Cancel</u></html>");
        cancel.setFont(Theme.font(Font.BOLD, 13));
        cancel.setForeground(Theme.PRIMARY);
        cancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                dispose();
            }
        });

        for (JComponent c : new JComponent[]{heading, hint, scroll, totalLabel, errorLabel, recordButton}) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        cancel.setAlignmentX(Component.CENTER_ALIGNMENT);

        content.add(heading);
        content.add(Box.createVerticalStrut(8));
        content.add(hint);
        content.add(Box.createVerticalStrut(20));
        content.add(scroll);
        content.add(Box.createVerticalStrut(16));
        content.add(totalLabel);
        content.add(Box.createVerticalStrut(8));
        content.add(errorLabel);
        content.add(Box.createVerticalStrut(12));
        content.add(recordButton);
        content.add(Box.createVerticalStrut(16));
        content.add(cancel);

        setContentPane(content);
        loadShares();
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    /** True if Record Payment succeeded; ExpensesScreen uses it to decide what message to show. */
    public boolean wasPaymentRecorded() {
        return paymentRecorded;
    }

    // ---------- Loading ----------

    /** (Re)builds the checkbox list from the service. Also used after a failed submit to show fresh data. */
    private void loadShares() {
        shareList.removeAll();
        boxes.clear();
        rows.clear();
        errorLabel.setText(" ");

        try {
            rows.addAll(balanceService.getUnsettledSharesOwedTo(creditor.getId()));
        } catch (SettlrException ex) {
            errorLabel.setText(ex.getMessage());
        }

        if (rows.isEmpty()) {
            JLabel empty = new JLabel("  Nothing to pay " + creditor.getDisplayName()
                    + " right now. You're all settled!");
            empty.setFont(Theme.font(Font.PLAIN, 14));
            empty.setForeground(Theme.MUTED_TEXT);
            shareList.add(empty);
        }
        for (BalanceService.ShareRow row : rows) {
            JCheckBox box = new JCheckBox(describe(row));
            box.setOpaque(false);
            box.setFocusPainted(false);
            box.setFont(Theme.font(Font.PLAIN, 14));
            box.setForeground(Theme.TEXT);
            // A share that already has a pending payment cannot be paid twice (rule 3.6).
            box.setEnabled(!row.isPaymentPending());
            box.addItemListener(e -> updateTotal());
            boxes.add(box);
            shareList.add(box);
        }
        updateTotal();
        shareList.revalidate();
        shareList.repaint();
    }

    private String describe(BalanceService.ShareRow row) {
        String text = row.getExpenseTitle() + "  |  due " + row.getDueDate().format(DUE_FORMAT)
                + "  |  " + MoneyUtil.format(row.getAmount());
        return row.isPaymentPending() ? text + "  |  waiting for confirmation" : text;
    }

    // ---------- Actions ----------

    /** Adds up the ticked shares with BigDecimal and enables the button only when something is ticked. */
    private void updateTotal() {
        BigDecimal total = BigDecimal.ZERO;
        boolean anyTicked = false;
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).isSelected()) {
                total = total.add(rows.get(i).getAmount());
                anyTicked = true;
            }
        }
        totalLabel.setText("Total: " + MoneyUtil.format(total));
        recordButton.setEnabled(anyTicked);
    }

    private List<Integer> selectedShareIds() {
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).isSelected()) {
                ids.add(rows.get(i).getShareId());
            }
        }
        return ids;
    }

    private void onRecord() {
        List<Integer> ids = selectedShareIds();
        if (ids.isEmpty()) {
            errorLabel.setText("Please tick at least one share to pay.");
            return;
        }
        recordButton.setEnabled(false); // stops a second click from sending the payment twice
        try {
            paymentService.pay(ids);
        } catch (SettlrException ex) {
            String message = ex.getMessage();
            loadShares();                // show the real, current state
            errorLabel.setText(message);
            return;
        }
        paymentRecorded = true;
        String name = creditor.getDisplayName();
        JOptionPane.showMessageDialog(this,
                "Payment sent to " + name + " and waiting for their confirmation.\n"
                        + "It only counts once " + name + " confirms it.",
                "Payment pending", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }
}