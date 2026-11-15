package com.settlr.ui.screens;

import com.settlr.model.User;
import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.components.ProgressBar;
import com.settlr.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

import static com.settlr.ui.components.Ui.*;

/** "Carlo ... 3/3" with a bar under the name. */
class MemberProgressRow extends JPanel {

    /** count is {done, total} for this week. */
    MemberProgressRow(User u, int[] count, boolean me) {
        super(new BorderLayout(10, 0));
        setOpaque(false);
        setBorder(new EmptyBorder(6, 0, 6, 0));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        String name = me ? "You" : u.getDisplayName();
        boolean all = count[1] > 0 && count[0] == count[1];
        top.add(label(name + (all ? "  \u2713" : ""), Font.BOLD, 13, all ? Theme.MINT : Theme.TEXT),
                BorderLayout.WEST);
        top.add(label(count[1] == 0 ? "no tasks" : count[0] + "/" + count[1], Font.PLAIN, 12, Theme.MUTED_TEXT),
                BorderLayout.EAST);
        JPanel text = vertical();
        text.add(left(fitHeight(top)));
        text.add(Box.createVerticalStrut(5));
        text.add(left(new ProgressBar(count[0], count[1], 5)));
        add(ListRow.centeredVertically(new AvatarLabel(u, 26)), BorderLayout.WEST);
        add(text, BorderLayout.CENTER);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
    }
}
