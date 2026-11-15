package com.settlr.ui.screens;

import com.settlr.model.SwapRequest;
import com.settlr.model.User;
import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.theme.Theme;
import com.settlr.util.DateTimeUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

import static com.settlr.ui.components.Ui.*;

/** "Carlo asked you to take Wash Dishes / Tue \u00B7 8:00 PM \u00B7 1h ago   Decline [Accept]". */
class SwapRequestRow extends JPanel {

    private static final Color DIVIDER = new Color(232, 217, 205, 40);
    private static final String DOT = TasksScreen.DOT;

    /** fromUser may be null (they left); decline and accept are decided by the screen. */
    SwapRequestRow(SwapRequest s, User fromUser, boolean divider, Runnable decline, Runnable accept) {
        super(new BorderLayout(12, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(9, 0, 9, 0));
        String from = s.getFromUserName();
        JPanel text = vertical();
        text.add(left(label(from + " asked you to take " + s.getTaskTitle(), Font.BOLD, 14, Theme.TEXT)));
        text.add(Box.createVerticalStrut(2));
        text.add(left(label(TasksScreen.when(s.getAssignmentDueAt()) + DOT + DateTimeUtil.timeAgo(s.getCreatedAt()),
                Font.PLAIN, 11, Theme.MUTED_TEXT)));

        JLabel declineLink = TasksScreen.link("Decline", decline);
        RoundedButton acceptButton = new RoundedButton("Accept");
        acceptButton.setArc(RoundedButton.PILL);
        acceptButton.setFont(Theme.font(Font.BOLD, 12));
        acceptButton.setMargin(new Insets(0, 0, 0, 0));
        acceptButton.setPreferredSize(new Dimension(84, 30));
        acceptButton.addActionListener(e -> accept.run());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        actions.setOpaque(false);
        actions.add(declineLink);
        actions.add(acceptButton);

        add(ListRow.centeredVertically(fromUser != null ? new AvatarLabel(fromUser, 34) : new AvatarLabel(from, 34)),
                BorderLayout.WEST);
        add(ListRow.leftCentered(text), BorderLayout.CENTER);
        add(ListRow.centeredVertically(actions), BorderLayout.EAST);
        if (divider) {
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, DIVIDER), new EmptyBorder(9, 0, 9, 0)));
        }
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
    }
}
