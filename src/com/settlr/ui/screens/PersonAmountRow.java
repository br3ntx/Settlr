package com.settlr.ui.screens;

import com.settlr.service.SettlementService;
import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.theme.Theme;
import com.settlr.util.MoneyUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;

import static com.settlr.ui.components.Ui.*;

/**
 * One person inside the "You owe" / "You're owed" card when there's more than one:
 * avatar, name, the amount, and an action (Settle up, or a "waiting" badge) passed in by the screen.
 */
class PersonAmountRow extends JPanel {

    PersonAmountRow(SettlementService.Person p, boolean iOwe, JComponent action) {
        super(new BorderLayout(10, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(7, 0, 7, 0));
        String name = p.getUser().getDisplayName();
        BigDecimal amount = p.getNet().abs();

        JPanel who = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        who.setOpaque(false);
        who.add(new AvatarLabel(p.getUser(), 28));
        who.add(label(name, Font.BOLD, 13, Theme.TEXT));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        right.add(label(amount.signum() == 0 ? "Even" : MoneyUtil.format(amount), Font.BOLD, 14,
                amount.signum() == 0 ? Theme.MUTED_TEXT : (iOwe ? Theme.DANGER : Theme.MINT)));
        if (action != null) {
            right.add(action);
        }
        add(ListRow.leftCentered(who), BorderLayout.CENTER);
        add(ListRow.centeredVertically(right), BorderLayout.EAST);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(ListRow.DIVIDER);
        g.fillRect(0, 0, getWidth(), 1); // line above each person
    }
}
