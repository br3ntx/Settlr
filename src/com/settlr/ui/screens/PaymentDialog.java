package com.settlr.ui.screens;

import com.settlr.exception.SettlrException;
import com.settlr.model.User;
import com.settlr.service.BalanceService;
import com.settlr.service.PaymentService;
import com.settlr.ui.ModalPopup;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.theme.Theme;
import com.settlr.util.MoneyUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Settle up" popup (Figma: POP UP SETTLE UP; master doc section 5). Lists the unsettled
 * shares the current user owes ONE person, each with a checkbox, and a live total.
 * Record payment creates PENDING payments, so the wording always says the payment still
 * needs the receiver's confirmation. There is no amount field: a payment always covers a
 * whole share (the schema has no partial payments).
 *
 * Payment rules live in Keith's BalanceService and PaymentService; this class only shows them.
 * Open it with:
 *     PaymentDialog.open(this, ben, () -> reload());
 * The last argument runs after a payment was recorded.
 */
public class PaymentDialog extends ModalPopup {

    private static final int FORM_WIDTH = 470;  // width of everything inside the card
    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private final BalanceService balanceService = new BalanceService();
    private final PaymentService paymentService = new PaymentService();

    private final User creditor;
    private final Runnable onRecorded;
    private final List<ShareRowView> rowViews = new ArrayList<>();

    private final JPanel rowsPanel = new JPanel();
    private final JLabel selectAll = new JLabel();
    private final JLabel selectedLabel = new JLabel(" ");
    private final JLabel totalLabel = new JLabel(" ");
    private final JLabel errorLabel = new JLabel(" "); // a space keeps its height when empty
    private final RoundedButton recordButton = new RoundedButton("Record payment");

    /** Opens the popup for paying creditor, over the window that contains anyComponent. */
    public static void open(Component anyComponent, User creditor, Runnable onRecorded) {
        if (creditor == null) {
            throw new IllegalArgumentException("Please choose who to pay.");
        }
        new PaymentDialog(creditor, onRecorded).showOver(anyComponent);
    }

    private PaymentDialog(User creditor, Runnable onRecorded) {
        this.creditor = creditor;
        this.onRecorded = onRecorded;
        add(buildCard());
        loadShares();
    }

    // ---------- layout ----------

    private GlassCard buildCard() {
        GlassCard card = new GlassCard(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 28, 24, 28));

        // Header: avatar, "Pay Ben", hint, X
        String name = creditor.getDisplayName();
        JLabel heading = label("Pay " + name, Font.BOLD, 24, Theme.TEXT);
        JLabel hint = label("Tick what you already paid. It counts once " + name + " confirms.",
                Font.PLAIN, 12, Theme.MUTED_TEXT);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        titles.add(heading);
        titles.add(hint);
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.add(new Avatar(name), BorderLayout.WEST);
        header.add(titles, BorderLayout.CENTER);
        JPanel closeHolder = new JPanel(new BorderLayout());
        closeHolder.setOpaque(false);
        closeHolder.add(createCloseButton(), BorderLayout.NORTH);
        header.add(closeHolder, BorderLayout.EAST);
        row(card, header, 0, 50);

        // "SHARES TO PAY ....... Select all"
        selectAll.setFont(Theme.font(Font.PLAIN, 12));
        selectAll.setForeground(Theme.TEXT);
        selectAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        selectAll.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                boolean selectEverything = !allSelected();
                for (ShareRowView v : rowViews) {
                    if (v.payable()) {
                        v.setSelected(selectEverything);
                    }
                }
                updateTotal();
            }
        });
        JPanel sectionRow = new JPanel(new BorderLayout());
        sectionRow.setOpaque(false);
        sectionRow.add(label("SHARES TO PAY", Font.BOLD, 12, Theme.AMBER), BorderLayout.WEST);
        sectionRow.add(selectAll, BorderLayout.EAST);
        row(card, sectionRow, 16, 18);

        // The share rows (scroll if there are many)
        rowsPanel.setOpaque(false);
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(rowsPanel,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
            @Override
            public Dimension getPreferredSize() {
                int h = Math.min(rowsPanel.getPreferredSize().height, 4 * 58); // about 4 rows, then scroll
                return new Dimension(FORM_WIDTH, Math.max(h, 52));
            }
        };
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        card.add(Box.createVerticalStrut(8));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(scroll);

        // "1 selected ........ Total P375.00"
        selectedLabel.setFont(Theme.font(Font.PLAIN, 13));
        selectedLabel.setForeground(Theme.MUTED_TEXT);
        totalLabel.setFont(Theme.font(Font.BOLD, 22));
        totalLabel.setForeground(Theme.TEXT);
        JPanel totalRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        totalRight.setOpaque(false);
        totalRight.add(label("Total", Font.PLAIN, 14, Theme.MUTED_TEXT));
        totalRight.add(totalLabel);
        JPanel totals = new JPanel(new BorderLayout());
        totals.setOpaque(false);
        totals.add(selectedLabel, BorderLayout.WEST);
        totals.add(totalRight, BorderLayout.EAST);
        row(card, totals, 14, 30);

        errorLabel.setFont(Theme.font(Font.PLAIN, 12));
        errorLabel.setForeground(Theme.DANGER);
        row(card, errorLabel, 4, 16);

        // Cancel | Record payment
        RoundedButton cancel = RoundedButton.outline("Cancel");
        cancel.setArc(RoundedButton.PILL);
        cancel.setFont(Theme.font(Font.BOLD, 14));
        cancel.addActionListener(e -> close());
        recordButton.setArc(RoundedButton.PILL);
        recordButton.setFont(Theme.font(Font.BOLD, 14));
        recordButton.addActionListener(e -> onRecord());
        JPanel buttons = new JPanel(new GridLayout(1, 2, 16, 0));
        buttons.setOpaque(false);
        buttons.add(cancel);
        buttons.add(recordButton);
        row(card, buttons, 8, 40);
        return card;
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

    private static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        return l;
    }

    // ---------- loading ----------

    /** (Re)builds the share rows from the service. Also used after a failed submit to show fresh data. */
    private void loadShares() {
        rowsPanel.removeAll();
        rowViews.clear();
        errorLabel.setText(" ");

        List<BalanceService.ShareRow> rows = new ArrayList<>();
        try {
            rows.addAll(balanceService.getUnsettledSharesOwedTo(creditor.getId()));
        } catch (SettlrException ex) {
            errorLabel.setText(ex.getMessage());
        }
        if (rows.isEmpty()) {
            JLabel empty = label("Nothing to pay " + creditor.getDisplayName() + " right now. You're all settled!",
                    Font.PLAIN, 13, Theme.MUTED_TEXT);
            empty.setBorder(new EmptyBorder(14, 4, 14, 0));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            rowsPanel.add(empty);
        }
        for (BalanceService.ShareRow r : rows) {
            ShareRowView view = new ShareRowView(r);
            rowViews.add(view);
            rowsPanel.add(view);
            rowsPanel.add(Box.createVerticalStrut(8));
        }
        updateTotal();
        rowsPanel.revalidate();
        rowsPanel.repaint();
    }

    // ---------- actions ----------

    private boolean allSelected() {
        boolean any = false;
        for (ShareRowView v : rowViews) {
            if (v.payable()) {
                any = true;
                if (!v.isSelected()) {
                    return false;
                }
            }
        }
        return any;
    }

    /** Adds up the ticked shares with BigDecimal and enables Record payment only when something is ticked. */
    private void updateTotal() {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        boolean anyPayable = false;
        for (ShareRowView v : rowViews) {
            anyPayable |= v.payable();
            if (v.isSelected()) {
                total = total.add(v.row.getAmount());
                count++;
            }
        }
        selectedLabel.setText(count + " selected");
        totalLabel.setText(MoneyUtil.format(total));
        recordButton.setEnabled(count > 0);
        selectAll.setText(anyPayable ? "<html><u>" + (allSelected() ? "Clear all" : "Select all") + "</u></html>" : " ");
    }

    private void onRecord() {
        List<Integer> ids = new ArrayList<>();
        for (ShareRowView v : rowViews) {
            if (v.isSelected()) {
                ids.add(v.row.getShareId());
            }
        }
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
        close();
        if (onRecorded != null) {
            onRecorded.run();            // the screen refreshes and says it's waiting for confirmation
        }
    }

    // ---------- pieces ----------

    /** One share: checkbox, "Wifi", "Due Oct 5", amount. Click anywhere on it to tick it. */
    private class ShareRowView extends JComponent {
        final BalanceService.ShareRow row;
        private boolean selected;
        private boolean hover;

        ShareRowView(BalanceService.ShareRow row) {
            this.row = row;
            Dimension d = new Dimension(FORM_WIDTH, 50);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            if (payable()) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { setSelected(!selected); updateTotal(); }
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                });
            } else {
                setToolTipText("Already paid. Waiting for " + creditor.getDisplayName() + " to confirm.");
            }
        }

        /** A share with a payment already waiting can't be paid twice (rule 3.6). */
        boolean payable() {
            return !row.isPaymentPending();
        }

        boolean isSelected() {
            return selected;
        }

        void setSelected(boolean selected) {
            this.selected = selected && payable();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            if (!payable()) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
            }
            Shape box = new RoundRectangle2D.Float(1, 1, w - 2, h - 2, 14, 14);
            g2.setColor(selected ? Theme.BANNER : new Color(255, 255, 255, hover ? 16 : 8));
            g2.fill(box);
            g2.setStroke(new BasicStroke(1.4f));
            g2.setColor(selected ? new Color(201, 164, 110, 170) : new Color(255, 255, 255, 55));
            g2.draw(box);

            // checkbox
            int cb = 18, cx = 16, cy = (h - cb) / 2;
            Shape square = new RoundRectangle2D.Float(cx, cy, cb, cb, 5, 5);
            if (selected) {
                g2.setColor(Theme.AMBER);
                g2.fill(square);
                g2.setColor(Theme.ON_PRIMARY);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(new int[]{cx + 4, cx + 8, cx + 14}, new int[]{cy + 9, cy + 13, cy + 5}, 3);
            } else {
                g2.setColor(new Color(255, 255, 255, 120));
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(square);
            }

            // title and due date
            int tx = cx + cb + 14;
            g2.setFont(Theme.font(Font.BOLD, 14));
            g2.setColor(Theme.TEXT);
            g2.drawString(row.getExpenseTitle(), tx, h / 2 - 2);
            g2.setFont(Theme.font(Font.PLAIN, 11));
            g2.setColor(Theme.MUTED_TEXT);
            String sub = "Due " + DUE.format(row.getDueDate())
                    + (payable() ? "" : "  \u2022  waiting for " + creditor.getDisplayName() + " to confirm");
            g2.drawString(sub, tx, h / 2 + 13);

            // amount
            g2.setFont(Theme.font(Font.BOLD, 18));
            g2.setColor(Theme.TEXT);
            String amount = MoneyUtil.format(row.getAmount());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(amount, w - 16 - fm.stringWidth(amount), (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Grey circle with the person's first letter. */
    private static class Avatar extends JComponent {
        private static final int SIZE = 46;
        private final String letter;

        Avatar(String name) {
            letter = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            setPreferredSize(new Dimension(SIZE, SIZE));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int y = (getHeight() - SIZE) / 2;
            g2.setColor(new Color(255, 255, 255, 45));
            g2.fill(new Ellipse2D.Float(0, y, SIZE, SIZE));
            g2.setFont(Theme.font(Font.BOLD, 18));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Theme.TEXT);
            g2.drawString(letter, (SIZE - fm.stringWidth(letter)) / 2, y + (SIZE - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
