package com.settlr.ui.screens;

import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.model.PayMethod;
import com.settlr.model.User;
import com.settlr.service.SettlementService;
import com.settlr.ui.ModalPopup;
import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.SegmentedControl;
import com.settlr.ui.components.SlimScrollBarUI;
import com.settlr.ui.components.StyledTextField;
import com.settlr.ui.theme.Theme;
import com.settlr.util.MoneyUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.settlr.ui.components.Ui.*;

/**
 * "Pay Ana" (master doc section 5, net version).
 *
 * Pay all: one payment clears everything between the two of you. What Ana owes you (bills you
 * covered for her) is taken off what you owe her, shown like a receipt:
 *     Your shares P6,180.00 / - Ana owes you P333.33 / You pay Ana P5,846.67
 * "See breakdown" lists the expenses behind each line.
 *
 * Choose expenses: tick only YOUR shares you're paying now. What Ana owes you isn't touched
 * (it stays open for her to pay later), so every tick box means "I'm paying this one".
 *
 * Shares that already have a separate payment waiting are left out. The rules live in
 * SettlementService; this popup only shows them and sends the request. Open it with:
 *     PaymentDialog.open(this, person, () -> reload());
 */
public class PaymentDialog extends ModalPopup {

    private static final int FORM_WIDTH = 420;
    private static final int MAX_BREAKDOWN_HEIGHT = 230;
    private static final int MAX_BREAKDOWN_WITH_PAY_INFO = 160; // keeps the popup inside a 700px window
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final String DOT = " \u00B7 ";

    private final SettlementService settlementService = new SettlementService();
    private final SettlementService.Person person;
    private final Runnable onSent;
    private final String name;
    private final List<SettlementService.ShareLine> mine;
    private final List<SettlementService.ShareLine> theirs;

    /** Your share ids ticked in "Choose expenses". Starts with all of them ticked. */
    private final Set<Integer> picked = new LinkedHashSet<>();
    private final Map<Integer, Check> checks = new HashMap<>();
    private boolean choosing;
    private boolean breakdownOpen;

    private final StyledTextField noteField = new StyledTextField("e.g. GCash ref 1234, cash");
    private final JLabel amountLabel = label(" ", Font.BOLD, 38, Theme.TEXT);
    private final JLabel coversLabel = label(" ", Font.PLAIN, 12, Theme.MUTED_TEXT);
    private final JLabel toggle = new JLabel();
    private final JLabel mineTotal = label(" ", Font.BOLD, 13, Theme.TEXT);
    private final JLabel errorLabel = new JLabel(" ");
    private final RoundedButton sendButton = new RoundedButton("Mark as paid");

    // pieces of the breakdown box that show or hide depending on the mode
    private final List<JComponent> mineItems = new ArrayList<>();
    private final List<JComponent> theirItems = new ArrayList<>();
    private final List<JComponent> receiptOnly = new ArrayList<>(); // Ana's part and the total line
    private JPanel box;
    private JScrollPane breakdown;
    private Component breakdownGap;
    private GlassCard card;
    private int maxBreakdown = MAX_BREAKDOWN_HEIGHT;

    /** The amount a settle up with this person would pay right now (shares with a payment waiting are left out). */
    public static BigDecimal amountToPay(SettlementService.Person person) {
        return sum(payable(person.getIOwe())).subtract(sum(payable(person.getTheyOwe()))).max(BigDecimal.ZERO);
    }

    /** Opens the popup over the window that contains anyComponent. onSent runs after it was sent. */
    public static void open(Component anyComponent, SettlementService.Person person, Runnable onSent) {
        new PaymentDialog(person, onSent).showOver(anyComponent);
    }

    private PaymentDialog(SettlementService.Person person, Runnable onSent) {
        this.person = person;
        this.onSent = onSent;
        this.name = person.getUser().getDisplayName();
        this.mine = payable(person.getIOwe());
        this.theirs = payable(person.getTheyOwe());
        mine.forEach(l -> picked.add(l.getShareId()));
        StyledTextField.usePillStyle(noteField);
        noteField.setFont(Theme.font(Font.PLAIN, 12));
        add(buildCard());
        refresh();
    }

    private GlassCard buildCard() {
        card = new GlassCard(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 26, 20, 26));

        // header: avatar, "Pay Ana", close
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.add(new AvatarLabel(person.getUser(), 40), BorderLayout.WEST);
        header.add(label(amountToPay(person).signum() == 0 && !mine.isEmpty() && !theirs.isEmpty()
                ? "Even with " + name : "Pay " + name, Font.BOLD, 20, Theme.TEXT), BorderLayout.CENTER);
        JPanel closeHolder = new JPanel(new BorderLayout());
        closeHolder.setOpaque(false);
        closeHolder.add(createCloseButton(), BorderLayout.NORTH);
        header.add(closeHolder, BorderLayout.EAST);
        row(card, header, 0, 42);

        // Pay all | Choose expenses (only when choosing would change something)
        if (mine.size() > 1 || (!mine.isEmpty() && !theirs.isEmpty())) {
            SegmentedControl modes = new SegmentedControl(new String[] {"Pay all", "Choose expenses"}, i -> {
                choosing = i == 1;
                refresh();
            });
            row(card, centered(modes), 14, modes.getPreferredSize().height);
        }

        // the amount, and what it covers
        row(card, centered(amountLabel), 10, 48);
        JPanel covers = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        covers.setOpaque(false);
        covers.add(coversLabel);
        toggle.setFont(Theme.font(Font.BOLD, 12));
        toggle.setForeground(Theme.PRIMARY);
        toggle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggle.setHorizontalTextPosition(SwingConstants.LEADING);
        toggle.setIconTextGap(5);
        toggle.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                breakdownOpen = !breakdownOpen;
                refresh();
            }
        });
        covers.add(toggle);
        row(card, covers, 4, 18);

        // where to send it, from their "How to pay me" on the Profile page
        JComponent payTo = buildPayTo();
        if (payTo != null) {
            row(card, payTo, 12, 56);
            maxBreakdown = MAX_BREAKDOWN_WITH_PAY_INFO;
        }

        // the receipt / checklist
        box = buildBreakdown();
        breakdown = new JScrollPane(box,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        breakdown.setBorder(null);
        breakdown.setOpaque(false);
        breakdown.getViewport().setOpaque(false);
        breakdown.getVerticalScrollBar().setUnitIncrement(12);
        SlimScrollBarUI.apply(breakdown);
        breakdownGap = Box.createVerticalStrut(12);
        card.add(breakdownGap);
        row(card, breakdown, 0, 10); // real height is set in refresh()

        // optional note
        JPanel noteTitle = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        noteTitle.setOpaque(false);
        noteTitle.add(label("How did you pay? ", Font.BOLD, 12, Theme.PRIMARY));
        noteTitle.add(label("(optional)", Font.PLAIN, 12, Theme.MUTED_TEXT));
        noteTitle.setBorder(new EmptyBorder(0, 6, 0, 0));
        row(card, noteTitle, 16, 16);
        row(card, noteField, 5, 36);

        errorLabel.setFont(Theme.font(Font.PLAIN, 12));
        errorLabel.setForeground(Theme.DANGER);
        row(card, errorLabel, 2, 16);

        // buttons
        RoundedButton cancel = RoundedButton.outline("Cancel");
        cancel.setArc(RoundedButton.PILL);
        cancel.setFont(Theme.font(Font.BOLD, 14));
        cancel.addActionListener(e -> close());
        sendButton.setArc(RoundedButton.PILL);
        sendButton.setFont(Theme.font(Font.BOLD, 14));
        sendButton.addActionListener(e -> send());
        JPanel buttons = new JPanel(new GridBagLayout());
        buttons.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 14);
        buttons.add(cancel, c);
        c.weightx = 1.5;
        c.insets = new Insets(0, 0, 0, 0);
        buttons.add(sendButton, c);
        row(card, buttons, 4, 42);

        row(card, centered(label("It counts once " + name + " confirms it.",
                Font.PLAIN, 11, Theme.MUTED_TEXT)), 10, 16);
        return card;
    }

    /**
     * Pay all, like a receipt:          Choose expenses, a checklist:
     *   Your shares (Ana paid)  P6,180     Your shares (Ana paid)   P2,600
     *     Rent . Oct 1          P2,400       [x] Rent . Oct 1       P2,400
     *   - Ana owes you          P333.33      [ ] test . Oct 29        P800
     *     Water bill . Oct 19   P333.33      [x] pizza party . Nov 1  P200
     *   You pay Ana             P5,846.67
     * (the item lines under each heading show only after "See breakdown")
     */
    private JPanel buildBreakdown() {
        JPanel b = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Shape r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 16, 16);
                g2.setColor(new Color(255, 255, 255, 10));
                g2.fill(r);
                g2.setColor(new Color(255, 255, 255, 55));
                g2.draw(r);
                g2.dispose();
            }
        };
        b.setOpaque(false);
        b.setLayout(new BoxLayout(b, BoxLayout.Y_AXIS));
        b.setBorder(new EmptyBorder(8, 16, 10, 16));

        b.add(groupHeader("Your shares", "(" + name + " paid)", mineTotal, Font.BOLD));
        for (SettlementService.ShareLine l : mine) {
            JComponent line = item(l, true);
            mineItems.add(line);
            b.add(line);
        }
        if (!theirs.isEmpty()) {
            JLabel minus = label("\u2212 " + MoneyUtil.format(sum(theirs)), Font.BOLD, 13, Theme.MINT);
            JComponent theirHeader = groupHeader(name + " owes you", "(you covered it)", minus, Font.BOLD);
            theirHeader.setBorder(new EmptyBorder(8, 0, 4, 0));
            receiptOnly.add(theirHeader);
            b.add(theirHeader);
            for (SettlementService.ShareLine l : theirs) {
                JComponent line = item(l, false);
                theirItems.add(line);
                b.add(line);
            }
            JComponent rule = divider();
            receiptOnly.add(rule);
            b.add(rule);
            JLabel total = label(MoneyUtil.format(amountToPay(person)), Font.BOLD, 14, Theme.TEXT);
            JComponent totalLine = groupHeader("You pay " + name, "", total, Font.BOLD);
            receiptOnly.add(totalLine);
            b.add(totalLine);
            JLabel alsoPaid = label("When " + name + " confirms, " + possessive(name) + " "
                    + MoneyUtil.format(sum(theirs)) + " is marked paid too.", Font.PLAIN, 11, Theme.MUTED_TEXT);
            alsoPaid.setBorder(new EmptyBorder(2, 0, 0, 0));
            receiptOnly.add(fullWidth(alsoPaid));
            b.add(alsoPaid);
        }

        // shares already paid one by one stay out of it (only older payments can do this)
        List<String> separate = new ArrayList<>();
        for (SettlementService.ShareLine l : person.getIOwe()) {
            if (l.isPaymentPending()) {
                separate.add(l.getExpenseTitle() + DOT + DAY.format(l.getDueDate()));
            }
        }
        if (!separate.isEmpty()) {
            JLabel note = label("<html><div style='width:330px'>Left out: " + String.join(", ", separate)
                    + " (already sent, waiting for " + name + ")</div></html>", Font.PLAIN, 11, Theme.AMBER);
            note.setBorder(new EmptyBorder(8, 0, 0, 0));
            b.add(fullWidth(note));
        }
        return b;
    }

    /** "Send to Ben's GCash / 0917 123 4567 . Ben Santos  [Copy]", or null when Ben hasn't set it. */
    private JComponent buildPayTo() {
        User u = person.getUser();
        PayMethod m = u.getPayMethod();
        if (m == null) {
            return null;
        }
        JPanel box = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Shape r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 14, 14);
                g2.setColor(new Color(95, 208, 176, 18));
                g2.fill(r);
                g2.setColor(new Color(95, 208, 176, 90));
                g2.draw(r);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.setBorder(new EmptyBorder(8, 14, 8, 12));
        box.add(new ProfileScreen.MethodTile(m, 30), BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        if (m.needsAccount()) {
            JLabel top = label("Send to " + possessive(name) + " " + m.getLabel(), Font.PLAIN, 11, Theme.MUTED_TEXT);
            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            bottom.setOpaque(false);
            bottom.add(label(u.getPayNumber(), Font.BOLD, 14, Theme.TEXT));
            bottom.add(label(DOT + u.getPayName(), Font.PLAIN, 12, Theme.MUTED_TEXT));
            top.setAlignmentX(Component.LEFT_ALIGNMENT);
            bottom.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(top);
            text.add(bottom);

            RoundedButton copy = RoundedButton.outline("Copy");
            copy.setArc(RoundedButton.PILL);
            copy.setFont(Theme.font(Font.BOLD, 12));
            copy.setMargin(new Insets(0, 0, 0, 0));
            copy.setPreferredSize(new Dimension(72, 30));
            copy.addActionListener(e -> {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(u.getPayNumber()), null);
                copy.setText("Copied!");
                Timer back = new Timer(1500, ev -> copy.setText("Copy"));
                back.setRepeats(false);
                back.start();
            });
            JPanel copyHolder = new JPanel(new GridBagLayout());
            copyHolder.setOpaque(false);
            copyHolder.add(copy);
            box.add(copyHolder, BorderLayout.EAST);
        } else {
            JLabel top = label(name + " takes cash", Font.BOLD, 14, Theme.TEXT);
            JLabel bottom = label("Pay in person.", Font.PLAIN, 11, Theme.MUTED_TEXT);
            top.setAlignmentX(Component.LEFT_ALIGNMENT);
            bottom.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(top);
            text.add(bottom);
        }
        JPanel textHolder = new JPanel(new GridBagLayout()); // centers the two lines vertically
        textHolder.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.weightx = 1;
        gc.anchor = GridBagConstraints.WEST;
        textHolder.add(text, gc);
        box.add(textHolder, BorderLayout.CENTER);
        return box;
    }

    /** One expense line. Your lines get a tick box (shown while choosing); the whole line toggles it. */
    private JPanel item(SettlementService.ShareLine l, boolean tickable) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(3, 12, 3, 0));
        if (tickable) {
            Check check = new Check();
            checks.put(l.getShareId(), check);
            p.add(check, BorderLayout.WEST);
            p.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (!choosing) {
                        return;
                    }
                    if (!picked.remove(l.getShareId())) {
                        picked.add(l.getShareId());
                    }
                    refresh();
                }
            });
        }
        p.add(label(l.getExpenseTitle() + DOT + DAY.format(l.getDueDate()), Font.PLAIN, 12, Theme.MUTED_TEXT),
                BorderLayout.CENTER);
        p.add(label(MoneyUtil.format(l.getAmount()), Font.PLAIN, 12, Theme.TEXT), BorderLayout.EAST);
        return fullWidth(p);
    }

    /** Updates everything that depends on the mode and the ticks. */
    private void refresh() {
        List<SettlementService.ShareLine> myLines = choosing ? pickedOf(mine) : mine;
        BigDecimal subtracted = choosing ? BigDecimal.ZERO : sum(theirs);
        BigDecimal amount = sum(myLines).subtract(subtracted).max(BigDecimal.ZERO);
        int count = myLines.size();

        amountLabel.setText(MoneyUtil.format(amount));
        amountLabel.setForeground(choosing && count == 0 ? Theme.MUTED_TEXT : Theme.TEXT);
        int all = mine.size() + theirs.size();
        coversLabel.setText(choosing
                ? "Paying for " + count + " of your " + mine.size() + " share" + (mine.size() == 1 ? "" : "s")
                : "Covers " + all + " shared expense" + (all == 1 ? "" : "s") + DOT);
        toggle.setVisible(!choosing);
        toggle.setText(breakdownOpen ? "Hide breakdown" : "See breakdown");
        toggle.setIcon(new Chevron(breakdownOpen));

        // what the box shows
        boolean showItems = choosing || breakdownOpen;
        mineTotal.setText(MoneyUtil.format(sum(myLines)));
        mineItems.forEach(c -> c.setVisible(showItems));
        theirItems.forEach(c -> c.setVisible(!choosing && breakdownOpen));
        receiptOnly.forEach(c -> c.setVisible(!choosing));
        for (Map.Entry<Integer, Check> e : checks.entrySet()) {
            e.getValue().setVisible(choosing);
            e.getValue().setOn(picked.contains(e.getKey()));
            Container line = e.getValue().getParent();
            if (line != null) {
                line.setCursor(choosing ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        }
        // Pay all with nothing to subtract: the box would only repeat the big amount, so it waits for "See breakdown"
        boolean showBox = choosing || breakdownOpen || !theirs.isEmpty();
        breakdown.setVisible(showBox);
        breakdownGap.setVisible(showBox);
        int height = Math.min(box.getPreferredSize().height, maxBreakdown);
        Dimension d = new Dimension(FORM_WIDTH, height);
        breakdown.setPreferredSize(d);
        breakdown.setMinimumSize(d);
        breakdown.setMaximumSize(d);

        errorLabel.setText(choosing && count == 0 ? "Tick at least one of your shares to pay " + name + "." : " ");
        sendButton.setEnabled(!choosing || count > 0);
        sendButton.setText(amount.signum() == 0 ? "Mark as settled" : "Mark as paid");

        box.revalidate();
        card.revalidate();
        revalidate();
        repaint();
    }

    private void send() {
        errorLabel.setText(" ");
        sendButton.setEnabled(false); // stops a second click from sending it twice
        try {
            settlementService.settleUp(person.getUser().getId(), noteField.getText(),
                    choosing ? new ArrayList<>(picked) : null);
        } catch (DataAccessException e) {
            errorLabel.setText("Can't save right now. Is MySQL running?");
            sendButton.setEnabled(true);
            e.printStackTrace();
            return;
        } catch (SettlrException e) {
            errorLabel.setText(e.getMessage());
            sendButton.setEnabled(true);
            return;
        }
        close();
        if (onSent != null) {
            onSent.run();
        }
    }

    // ---------- small helpers ----------

    private List<SettlementService.ShareLine> pickedOf(List<SettlementService.ShareLine> lines) {
        List<SettlementService.ShareLine> result = new ArrayList<>();
        for (SettlementService.ShareLine l : lines) {
            if (picked.contains(l.getShareId())) {
                result.add(l);
            }
        }
        return result;
    }

    private static List<SettlementService.ShareLine> payable(List<SettlementService.ShareLine> lines) {
        List<SettlementService.ShareLine> result = new ArrayList<>();
        for (SettlementService.ShareLine l : lines) {
            if (!l.isPaymentPending()) {
                result.add(l);
            }
        }
        return result;
    }

    private static BigDecimal sum(List<SettlementService.ShareLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (SettlementService.ShareLine l : lines) {
            total = total.add(l.getAmount());
        }
        return total;
    }

    private static JPanel groupHeader(String title, String hint, JLabel amount, int style) {
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(label(title + " ", style, 13, Theme.TEXT));
        left.add(label(hint, Font.PLAIN, 11, Theme.MUTED_TEXT));
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(6, 0, 4, 0));
        p.add(left, BorderLayout.CENTER);
        p.add(amount, BorderLayout.EAST);
        return fullWidth(p);
    }

    /** "Ana's", or "Chris'" for a name ending in s. */
    private static String possessive(String name) {
        return name.endsWith("s") ? name + "'" : name + "'s";
    }

    private static JComponent divider() {
        JPanel line = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(new Color(232, 217, 205, 60));
                g.fillRect(0, getHeight() / 2, getWidth(), 1);
            }
        };
        line.setOpaque(false);
        line.setPreferredSize(new Dimension(10, 13));
        return fullWidth(line);
    }

    private static <T extends JComponent> T fullWidth(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    private static JPanel centered(JComponent c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        p.setOpaque(false);
        p.add(c);
        return p;
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

    // ---------- small drawn pieces ----------

    /** A tick box: beige with a dark tick when on, an outline when off. */
    private static final class Check extends JComponent {
        private boolean on;

        Check() {
            Dimension d = new Dimension(16, 16);
            setPreferredSize(d);
            setMinimumSize(d);
        }

        void setOn(boolean on) {
            this.on = on;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int s = 15, y = (getHeight() - s) / 2;
            Shape box = new RoundRectangle2D.Float(0.5f, y + 0.5f, s - 1, s - 1, 5, 5);
            if (on) {
                g2.setColor(Theme.PRIMARY);
                g2.fill(box);
                g2.setColor(Theme.ON_PRIMARY);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(new int[] {4, 7, 11}, new int[] {y + 8, y + 11, y + 4}, 3);
            } else {
                g2.setColor(Theme.INPUT_BORDER);
                g2.setStroke(new BasicStroke(1.4f));
                g2.draw(box);
            }
            g2.dispose();
        }
    }

    /** A small arrow drawn in code (the app font has no arrow characters). Up when open, down when closed. */
    private static final class Chevron implements Icon {
        private final boolean up;

        Chevron(boolean up) {
            this.up = up;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c.getForeground());
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int top = y + 3, bottom = y + 7;
            if (up) {
                g2.drawPolyline(new int[] {x, x + 4, x + 8}, new int[] {bottom, top, bottom}, 3);
            } else {
                g2.drawPolyline(new int[] {x, x + 4, x + 8}, new int[] {top, bottom, top}, 3);
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 9;
        }

        @Override
        public int getIconHeight() {
            return 10;
        }
    }
}
