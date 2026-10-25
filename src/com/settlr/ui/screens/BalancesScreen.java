package com.settlr.ui.screens;

import com.settlr.exception.SettlrException;
import com.settlr.model.Balance;
import com.settlr.service.BalanceService;
import com.settlr.service.PaymentService;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.BaseScreen;
import com.settlr.ui.theme.Theme;
import com.settlr.util.DateTimeUtil;
import com.settlr.util.MoneyUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Balances screen (master doc section 5): one row per person with the net amount and a
 * Settle Up button, plus a section for payments waiting for MY confirmation. It only shows
 * data and calls services; all rules (who may confirm, what is owed) live in the services.
 * Extends BaseScreen, so the Navigator calls onShow() (the reload hook) every time it appears.
 */
public class BalancesScreen extends BaseScreen {

    /** One service call that can fail with a readable message. */
    private interface PaymentAction {
        void run() throws SettlrException;
    }

    private final BalanceService balanceService = new BalanceService();
    private final PaymentService paymentService = new PaymentService();

    private final JLabel messageLabel = new JLabel(" "); // a space keeps its height when empty
    private final JLabel oweLabel = new JLabel(" ");
    private final JLabel owedLabel = new JLabel(" ");
    private final JPanel waitingList = new JPanel();
    private final JPanel balanceList = new JPanel();

    public BalancesScreen() {
        setBackground(Theme.BACKGROUND);
        setLayout(new BorderLayout());

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));

        JLabel title = new JLabel("Balances");
        title.setFont(Theme.font(Font.BOLD, 28));
        title.setForeground(Theme.TEXT);

        oweLabel.setFont(Theme.font(Font.BOLD, 14));
        oweLabel.setForeground(Theme.DANGER);
        owedLabel.setFont(Theme.font(Font.BOLD, 14));
        owedLabel.setForeground(Theme.PRIMARY);
        JPanel totals = new JPanel(new FlowLayout(FlowLayout.LEFT, 24, 0));
        totals.setOpaque(false);
        totals.add(oweLabel);
        totals.add(owedLabel);

        messageLabel.setFont(Theme.font(Font.PLAIN, 13));

        styleList(waitingList);
        styleList(balanceList);

        content.add(left(title));
        content.add(Box.createVerticalStrut(8));
        content.add(left(totals));
        content.add(Box.createVerticalStrut(8));
        content.add(left(messageLabel));
        content.add(Box.createVerticalStrut(16));
        content.add(left(sectionTitle("Waiting for your confirmation")));
        content.add(Box.createVerticalStrut(12));
        content.add(left(waitingList));
        content.add(Box.createVerticalStrut(24));
        content.add(left(sectionTitle("Who owes whom")));
        content.add(Box.createVerticalStrut(12));
        content.add(left(balanceList));

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    /** Reloads everything and clears old messages. The navigator will call this each time the screen appears. */
    @Override
    public void onShow() {
        showMessage(" ", false);
        reload();
    }

    // ---------- Loading ----------

    /** Reloads data without touching the message line, so a result message survives a refresh. */
    private void reload() {
        loadBalances();
        loadWaitingPayments();
        revalidate();
        repaint();
    }

    private void loadBalances() {
        balanceList.removeAll();
        try {
            List<Balance> balances = balanceService.getMyBalances();
            oweLabel.setText("You owe " + MoneyUtil.format(balanceService.getTotalIOwe()));
            owedLabel.setText("Owed to you " + MoneyUtil.format(balanceService.getTotalOwedToMe()));
            if (balances.isEmpty()) {
                balanceList.add(emptyState("It's just you here. Share your invite code to add roommates."));
            }
            for (Balance b : balances) {
                balanceList.add(buildBalanceRow(b));
                balanceList.add(Box.createVerticalStrut(8));
            }
        } catch (SettlrException ex) {
            oweLabel.setText(" ");
            owedLabel.setText(" ");
            showMessage(ex.getMessage(), true);
            balanceList.add(emptyState("Balances could not be loaded."));
        }
    }

    private void loadWaitingPayments() {
        waitingList.removeAll();
        try {
            List<BalanceService.PaymentRow> rows = balanceService.getIncomingPaymentRows();
            if (rows.isEmpty()) {
                waitingList.add(emptyState("No payments waiting for you."));
            }
            for (BalanceService.PaymentRow row : rows) {
                waitingList.add(buildWaitingRow(row));
                waitingList.add(Box.createVerticalStrut(8));
            }
        } catch (SettlrException ex) {
            showMessage(ex.getMessage(), true);
            waitingList.add(emptyState("Payments could not be loaded."));
        }
    }

    // ---------- Rows ----------

    private JPanel buildBalanceRow(Balance b) {
        String name = b.getOtherUser().getDisplayName();
        JLabel text = new JLabel();
        text.setFont(Theme.font(Font.BOLD, 14));
        if (b.iOweThem()) {
            text.setText("You owe " + name + " " + MoneyUtil.format(b.getAbsoluteAmount()));
            text.setForeground(Theme.DANGER);
        } else if (b.theyOweMe()) {
            text.setText(name + " owes you " + MoneyUtil.format(b.getAbsoluteAmount()));
            text.setForeground(Theme.PRIMARY);
        } else {
            text.setText(name + ": all settled");
            text.setForeground(Theme.MUTED_TEXT);
        }

        RoundedButton settle = new RoundedButton("Settle Up");
        settle.setPreferredSize(new Dimension(140, 44));
        settle.addActionListener(e -> onSettleUp(b));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(settle);
        return card(text, right);
    }

    private JPanel buildWaitingRow(BalanceService.PaymentRow row) {
        JLabel main = new JLabel(row.getFromUserName() + " paid " + MoneyUtil.format(row.getAmount())
                + " for " + row.getExpenseTitle());
        main.setFont(Theme.font(Font.BOLD, 14));
        main.setForeground(Theme.TEXT);
        JLabel when = new JLabel("Pending " + DateTimeUtil.timeAgo(row.getPaidAt())
                + ". It only counts once you confirm.");
        when.setFont(Theme.font(Font.PLAIN, 12));
        when.setForeground(Theme.MUTED_TEXT);

        JPanel textBox = new JPanel();
        textBox.setOpaque(false);
        textBox.setLayout(new BoxLayout(textBox, BoxLayout.Y_AXIS));
        main.setAlignmentX(Component.LEFT_ALIGNMENT);
        when.setAlignmentX(Component.LEFT_ALIGNMENT);
        textBox.add(main);
        textBox.add(when);

        RoundedButton confirm = new RoundedButton("Confirm");
        confirm.setPreferredSize(new Dimension(120, 44));
        confirm.addActionListener(e -> onConfirm(row));

        JLabel reject = new JLabel("<html><u>Reject</u></html>");
        reject.setFont(Theme.font(Font.BOLD, 13));
        reject.setForeground(Theme.DANGER);
        reject.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        reject.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onReject(row);
            }
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        right.setOpaque(false);
        right.add(confirm);
        right.add(reject);
        return card(textBox, right);
    }

    /** White bordered row: content on the left, actions on the right. */
    private JPanel card(JComponent leftSide, JComponent rightSide) {
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setBackground(Theme.SURFACE);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        row.add(leftSide, BorderLayout.CENTER);
        row.add(rightSide, BorderLayout.EAST);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    // ---------- Actions ----------

    private void onSettleUp(Balance b) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        boolean recorded = PaymentDialog.showFor(owner, b.getOtherUser());
        reload();
        if (recorded) {
            showMessage("Payment sent to " + b.getOtherUser().getDisplayName()
                    + ". It counts once they confirm it.", false);
        }
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
                        + "?\nThe share will stay unpaid.",
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
        if (error != null) {
            showMessage(error, true);
        } else {
            showMessage(successMessage, false);
        }
    }

    // ---------- Small helpers ----------

    private void showMessage(String text, boolean isError) {
        messageLabel.setForeground(isError ? Theme.DANGER : Theme.PRIMARY);
        messageLabel.setText(text);
    }

    private JLabel emptyState(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.PLAIN, 14));
        l.setForeground(Theme.MUTED_TEXT);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JLabel sectionTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.BOLD, 18));
        l.setForeground(Theme.TEXT);
        return l;
    }

    private void styleList(JPanel list) {
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
    }

    private JComponent left(JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    // ---------- Temporary preview entry point ----------

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Settlr - Balances (preview)");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1100, 700);
            frame.setMinimumSize(new Dimension(800, 600));
            frame.setLocationRelativeTo(null);
            BalancesScreen screen = new BalancesScreen();
            frame.add(screen);
            frame.setVisible(true);
            screen.onShow(); // without a logged-in Session this shows a friendly error, not a crash
        });
    }
}