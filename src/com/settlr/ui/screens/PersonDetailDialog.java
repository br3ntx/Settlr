package com.settlr.ui.screens;

import com.settlr.service.SettlementService;
import com.settlr.ui.ModalPopup;
import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.SlimScrollBarUI;
import com.settlr.ui.components.StatusBadge;
import com.settlr.ui.theme.Theme;
import com.settlr.util.MoneyUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Click a person on the Expenses page: everything open between you two, in both
 * directions, adding up to the one net number shown on their row.
 */
public class PersonDetailDialog extends ModalPopup {

    private static final int FORM_WIDTH = 440;
    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);

    private final SettlementService.Person person;
    private final Runnable onChanged;
    private final Component screen; // where Settle up opens after this popup closes

    /** Opens the breakdown. onChanged runs if a settle up was sent from here. */
    public static void open(Component anyComponent, SettlementService.Person person, Runnable onChanged) {
        new PersonDetailDialog(person, onChanged, anyComponent).showOver(anyComponent);
    }

    private PersonDetailDialog(SettlementService.Person person, Runnable onChanged, Component screen) {
        this.person = person;
        this.onChanged = onChanged;
        this.screen = screen;
        add(buildCard());
    }

    private GlassCard buildCard() {
        String name = person.getUser().getDisplayName();
        GlassCard card = new GlassCard(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 28, 24, 28));

        // header
        JPanel titles = vertical();
        titles.add(label(name, Font.BOLD, 22, Theme.TEXT));
        titles.add(label("Everything open between you and " + name, Font.PLAIN, 12, Theme.MUTED_TEXT));
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.add(new AvatarLabel(name, 46), BorderLayout.WEST);
        header.add(titles, BorderLayout.CENTER);
        JPanel closeHolder = new JPanel(new BorderLayout());
        closeHolder.setOpaque(false);
        closeHolder.add(createCloseButton(), BorderLayout.NORTH);
        header.add(closeHolder, BorderLayout.EAST);
        row(card, header, 0, 50);

        // the two lists
        JPanel lists = vertical();
        lists.add(left(section("YOU OWE " + name.toUpperCase(), Theme.DANGER)));
        addLines(lists, person.getIOwe());
        lists.add(Box.createVerticalStrut(10));
        lists.add(left(section(name.toUpperCase() + " OWES YOU", Theme.MINT)));
        addLines(lists, person.getTheyOwe());
        JScrollPane scroll = new JScrollPane(lists,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(FORM_WIDTH, Math.min(lists.getPreferredSize().height, 280));
            }
        };
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        SlimScrollBarUI.apply(scroll);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(Box.createVerticalStrut(14));
        card.add(scroll);

        // divider + the math
        JPanel divider = new JPanel();
        divider.setBackground(new Color(232, 217, 205, 70));
        row(card, divider, 10, 1);
        BigDecimal net = person.getNet();
        String math = MoneyUtil.format(person.getIOweTotal()) + " \u2212 " + MoneyUtil.format(person.getTheyOweTotal());
        JLabel result;
        if (net.signum() < 0) {
            result = label("You owe " + MoneyUtil.format(net.abs()), Font.BOLD, 20, Theme.DANGER);
        } else if (net.signum() > 0) {
            result = label(name + " owes you " + MoneyUtil.format(net), Font.BOLD, 20, Theme.MINT);
        } else {
            result = label("You're even", Font.BOLD, 20, Theme.TEXT);
        }
        JPanel total = new JPanel(new BorderLayout());
        total.setOpaque(false);
        total.add(label(math, Font.PLAIN, 13, Theme.MUTED_TEXT), BorderLayout.WEST);
        total.add(result, BorderLayout.EAST);
        row(card, total, 8, 30);

        // what's waiting, if anything
        String waiting = waitingText(name);
        if (waiting != null) {
            JLabel w = label(waiting, Font.PLAIN, 12, Theme.AMBER);
            row(card, w, 6, 18);
        }

        // buttons
        RoundedButton close = RoundedButton.outline("Close");
        close.setArc(RoundedButton.PILL);
        close.setFont(Theme.font(Font.BOLD, 14));
        close.addActionListener(e -> close());
        JPanel buttons = new JPanel(new GridLayout(1, person.canSettleUp() ? 2 : 1, 14, 0));
        buttons.setOpaque(false);
        buttons.add(close);
        if (person.canSettleUp()) {
            RoundedButton settle = new RoundedButton("Settle up " + MoneyUtil.format(PaymentDialog.amountToPay(person)));
            settle.setArc(RoundedButton.PILL);
            settle.setFont(Theme.font(Font.BOLD, 14));
            settle.addActionListener(e -> {
                close();
                PaymentDialog.open(screen, person, onChanged); // this popup is gone, so open over the screen
            });
            buttons.add(settle);
        }
        row(card, buttons, 16, 40);
        return card;
    }

    private String waitingText(String name) {
        if (person.getPendingFromMe() != null) {
            return "Your settle up of " + MoneyUtil.format(person.getPendingFromMe().getAmount())
                    + " is waiting for " + name + " to confirm.";
        }
        if (person.getPendingToMe() != null) {
            return name + " says they paid you " + MoneyUtil.format(person.getPendingToMe().getAmount())
                    + ". Confirm it on the Expenses page.";
        }
        if (person.getSentSeparately().signum() > 0) {
            return MoneyUtil.format(person.getSentSeparately()) + " you sent is waiting for " + name + " to confirm.";
        }
        if (person.getNet().signum() > 0) {
            return name + " settles up with you when they pay.";
        }
        return null;
    }

    private void addLines(JPanel list, List<SettlementService.ShareLine> lines) {
        if (lines.isEmpty()) {
            JLabel none = label("Nothing", Font.PLAIN, 13, Theme.MUTED_TEXT);
            none.setBorder(new EmptyBorder(4, 0, 4, 0));
            list.add(left(none));
            return;
        }
        for (SettlementService.ShareLine line : lines) {
            JPanel what = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            what.setOpaque(false);
            what.add(label(line.getExpenseTitle(), Font.PLAIN, 13, Theme.TEXT));
            what.add(label("\u2022 due " + DUE.format(line.getDueDate()), Font.PLAIN, 12, Theme.MUTED_TEXT));
            if (line.isPaymentPending()) {
                what.add(new StatusBadge("sent", StatusBadge.Kind.PENDING));
            }
            JPanel r = new JPanel(new BorderLayout());
            r.setOpaque(false);
            r.setBorder(new EmptyBorder(5, 0, 5, 0));
            r.add(what, BorderLayout.WEST);
            r.add(label(MoneyUtil.format(line.getAmount()), Font.BOLD, 13, Theme.TEXT), BorderLayout.EAST);
            r.setMaximumSize(new Dimension(Integer.MAX_VALUE, r.getPreferredSize().height));
            list.add(left(r));
        }
    }

    // ---------- small helpers ----------

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

    private static JLabel section(String text, Color color) {
        JLabel l = label(text, Font.BOLD, 11, color);
        l.setBorder(new EmptyBorder(0, 0, 4, 0));
        return l;
    }

    private static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        return l;
    }

    private static JPanel vertical() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    private static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }
}
