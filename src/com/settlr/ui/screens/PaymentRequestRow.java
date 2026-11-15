package com.settlr.ui.screens;

import com.settlr.service.PendingPayment;
import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.theme.Theme;
import com.settlr.util.DateTimeUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.settlr.ui.components.Ui.*;

/**
 * "Ben says they paid you P1,048.20 / GCash ref 1234 · just now   Not received [Confirm]".
 * One row type for both kinds of payment: PendingPayment hides which kind it is (Adapter).
 * What Confirm and Not received do is up to the screen, which passes them in.
 */
class PaymentRequestRow extends ListRow {

    private static final String DOT = " · ";

    PaymentRequestRow(PendingPayment p, boolean divider, Runnable confirm, Runnable reject) {
        super(divider, 9);
        JPanel text = vertical();
        text.add(left(label(p.getHeadline(), Font.BOLD, 14, Theme.TEXT)));
        text.add(Box.createVerticalStrut(2));
        text.add(left(label(p.getDetails() + DOT + DateTimeUtil.timeAgo(p.getSentAt()), Font.PLAIN, 11,
                Theme.MUTED_TEXT)));

        String rejectText = "Not received";
        JLabel rejectLink = label(rejectText, Font.BOLD, 12, Theme.PRIMARY);
        rejectLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        rejectLink.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { reject.run(); }
            @Override public void mouseEntered(MouseEvent e) { rejectLink.setText("<html><u>" + rejectText + "</u></html>"); }
            @Override public void mouseExited(MouseEvent e) { rejectLink.setText(rejectText); }
        });
        RoundedButton confirmButton = new RoundedButton("Confirm");
        confirmButton.setArc(RoundedButton.PILL);
        confirmButton.setFont(Theme.font(Font.BOLD, 12));
        confirmButton.setMargin(new Insets(0, 0, 0, 0));
        confirmButton.setPreferredSize(new Dimension(88, 30));
        confirmButton.addActionListener(e -> confirm.run());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        actions.setOpaque(false);
        actions.add(rejectLink);
        actions.add(confirmButton);

        add(new AvatarLabel(p.getFromName(), 34), BorderLayout.WEST);
        add(leftCentered(text), BorderLayout.CENTER);
        add(centeredVertically(actions), BorderLayout.EAST);
    }
}
