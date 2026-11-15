package com.settlr.ui.screens;

import com.settlr.model.Expense;
import com.settlr.model.ExpenseShare;
import com.settlr.ui.components.StatusBadge;
import com.settlr.ui.theme.Theme;
import com.settlr.util.MoneyUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.settlr.ui.components.Ui.*;

/**
 * One expense: title, "Ana paid P1,000 \u00B7 Nov 1 \u00B7 Rent", who still owes, and your share.
 * In Settled it just says "All paid".
 */
class ExpenseRow extends ListRow {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final String DOT = " \u00B7 ";
    private static final int MAX_CHIPS = 3;

    private final ExpenseList list;
    private final Set<Integer> expanded;

    /**
     * @param list     the loaded expenses, shares and names
     * @param expanded ids of expenses whose tags are open; shared with the screen so they stay open after a reload
     */
    ExpenseRow(Expense e, ExpenseList list, Set<Integer> expanded, boolean divider, boolean settled) {
        super(divider, 10);
        this.list = list;
        this.expanded = expanded;
        int me = list.getMe();
        String sub = list.nameOf(e.getPaidBy()) + " paid " + ExpensesScreen.compact(e.getAmount()) + DOT
                + DAY.format(e.getDueDate()) + DOT + ExpensesScreen.categoryName(e.getCategory())
                + (e.getBillId() != null ? DOT + "monthly" : "");

        JPanel text = vertical();
        text.add(left(label(e.getTitle(), Font.BOLD, 14, Theme.TEXT)));
        text.add(Box.createVerticalStrut(2));
        text.add(left(label(sub, Font.PLAIN, 11, Theme.MUTED_TEXT)));
        text.add(Box.createVerticalStrut(7));
        text.add(left(chips(e, me, settled)));

        ExpenseShare mine = null;
        for (ExpenseShare s : list.sharesFor(e)) {
            if (s.getUserId() == me) {
                mine = s;
            }
        }
        JLabel amount = label(mine == null ? "\u2014" : MoneyUtil.format(mine.getAmountOwed()),
                Font.BOLD, 14, mine == null ? Theme.MUTED_TEXT : Theme.TEXT);
        JLabel note = label(mine == null ? "not in it" : "your share", Font.PLAIN, 11, Theme.MUTED_TEXT);
        amount.setAlignmentX(Component.RIGHT_ALIGNMENT);
        note.setAlignmentX(Component.RIGHT_ALIGNMENT);
        JPanel right = vertical();
        right.add(amount);
        right.add(Box.createVerticalStrut(2));
        right.add(note);

        add(text, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    /**
     * "You \u00B7 Unpaid", "Carlo \u00B7 Sent", "Dani \u00B7 Paid", then "+1 paid" or "+2 more".
     * Clicking "+2 more" shows everyone's tag; "Show less" folds them back.
     * Settled expenses just say "All paid", with "See who" to show the same tags.
     */
    private JPanel chips(Expense e, int me, boolean settled) {
        List<ExpenseShare> shares = list.owedShares(e);
        shares.sort(Comparator.<ExpenseShare>comparingInt(s -> s.getUserId() == me ? 0 : 1)
                .thenComparing(s -> list.statusOf(s).ordinal())
                .thenComparing(s -> list.nameOf(s.getUserId()), String.CASE_INSENSITIVE_ORDER));
        List<JComponent> badges = new ArrayList<>();
        for (ExpenseShare s : shares) {
            ExpenseList.ShareStatus status = list.statusOf(s);
            String text = list.nameOf(s.getUserId()) + DOT
                    + (status == ExpenseList.ShareStatus.PAID ? "Paid" : status == ExpenseList.ShareStatus.SENT ? "Sent" : "Unpaid");
            StatusBadge.Kind kind = status == ExpenseList.ShareStatus.PAID ? StatusBadge.Kind.OK
                    : status == ExpenseList.ShareStatus.SENT ? StatusBadge.Kind.PENDING : StatusBadge.Kind.LIGHT;
            badges.add(new StatusBadge(text, kind));
        }
        JPanel holder = vertical();
        fillChips(holder, e.getId(), badges, shares, settled);
        return holder;
    }

    /** Lays out the tags: a few at most when folded, all of them when open, with the link that switches. */
    private void fillChips(JPanel holder, int expenseId, List<JComponent> badges, List<ExpenseShare> shares,
                           boolean settled) {
        holder.removeAll();
        Runnable redraw = () -> fillChips(holder, expenseId, badges, shares, settled);
        boolean open = expanded.contains(expenseId);
        if (settled && !open) {
            // Settled: just "All paid", and "See who" when there's anyone to show
            List<JComponent> parts = new ArrayList<>();
            parts.add(new StatusBadge("All paid", StatusBadge.Kind.OK));
            if (!badges.isEmpty()) {
                parts.add(link("See who", () -> {
                    expanded.add(expenseId);
                    redraw.run();
                }));
            }
            holder.add(left(chipRow(parts.toArray(new JComponent[0]))));
        } else if (!settled && (!open || badges.size() <= MAX_CHIPS)) {
            List<JComponent> parts = new ArrayList<>(badges.subList(0, Math.min(MAX_CHIPS, badges.size())));
            int rest = badges.size() - MAX_CHIPS;
            if (rest > 0) {
                boolean allPaid = true;
                for (ExpenseShare s : shares.subList(MAX_CHIPS, shares.size())) {
                    allPaid &= s.isSettled();
                }
                parts.add(link("+" + rest + (allPaid ? " paid" : " more"), () -> {
                    expanded.add(expenseId);
                    redraw.run();
                }));
            }
            holder.add(left(chipRow(parts.toArray(new JComponent[0]))));
        } else {
            JComponent less = link("Show less", () -> {
                expanded.remove(expenseId);
                redraw.run();
            });
            for (int i = 0; i < badges.size(); i += MAX_CHIPS) {
                List<JComponent> parts = new ArrayList<>(badges.subList(i, Math.min(i + MAX_CHIPS, badges.size())));
                boolean last = i + MAX_CHIPS >= badges.size();
                if (last && parts.size() < MAX_CHIPS) {
                    parts.add(less);
                }
                if (i > 0) {
                    holder.add(Box.createVerticalStrut(6));
                }
                holder.add(left(chipRow(parts.toArray(new JComponent[0]))));
            }
            if (badges.size() % MAX_CHIPS == 0) {
                holder.add(Box.createVerticalStrut(6));
                holder.add(left(chipRow(less)));
            }
        }
        holder.revalidate();
        holder.repaint();
    }

    /** Small beige text that acts like a link. */
    private JLabel link(String text, Runnable action) {
        JLabel l = label(text, Font.BOLD, 11, Theme.PRIMARY);
        l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        l.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { action.run(); }
            @Override public void mouseEntered(MouseEvent e) { l.setText("<html><u>" + text + "</u></html>"); }
            @Override public void mouseExited(MouseEvent e) { l.setText(text); }
        });
        return l;
    }

    /** The given tags in one line, 6 pixels apart. */
    private JPanel chipRow(JComponent... parts) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                p.add(Box.createHorizontalStrut(6));
            }
            p.add(parts[i]);
        }
        return p;
    }
}
