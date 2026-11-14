package com.settlr.ui.screens;

import com.settlr.model.Household;
import com.settlr.ui.ModalPopup;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.RoundRectangle2D;

import static com.settlr.ui.components.Ui.*;

/**
 * "Casa Mabini is ready!" right after creating a household: the invite code, big, with a Copy
 * button, so the new Admin can send it to their roommates. Every way out (Go to Home, the X,
 * Esc) runs onDone, which goes to Home.
 */
final class InviteCodeDialog extends ModalPopup {

    private static final int FORM_WIDTH = 368;

    private final Runnable onDone;
    private boolean finished;

    static void open(Component over, Household household, Runnable onDone) {
        new InviteCodeDialog(household, onDone).showOver(over);
    }

    private InviteCodeDialog(Household household, Runnable onDone) {
        this.onDone = onDone;
        GlassCard card = new GlassCard(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 26, 22, 26));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(label(household.getName() + " is ready!", Font.BOLD, 20, Theme.TEXT), BorderLayout.CENTER);
        JPanel closeHolder = new JPanel(new BorderLayout());
        closeHolder.setOpaque(false);
        closeHolder.add(createCloseButton(), BorderLayout.NORTH);
        header.add(closeHolder, BorderLayout.EAST);
        fixed(header, 32);
        card.add(header);

        JLabel sub = label("<html>You're the Admin. Send this invite code to your roommates so they can join.</html>",
                Font.PLAIN, 12, Theme.MUTED_TEXT);
        card.add(Box.createVerticalStrut(4));
        fixed(sub, 34);
        card.add(sub);

        card.add(Box.createVerticalStrut(14));
        JPanel box = codeBox(household.getInviteCode());
        fixed(box, 84);
        card.add(box);

        JLabel later = label("You can find it again any time in Household.", Font.PLAIN, 12, Theme.MUTED_TEXT);
        card.add(Box.createVerticalStrut(10));
        fixed(later, 18);
        card.add(later);

        RoundedButton go = new RoundedButton("Go to Home");
        go.setArc(RoundedButton.PILL);
        go.setFont(Theme.font(Font.BOLD, 14));
        go.addActionListener(e -> close());
        card.add(Box.createVerticalStrut(18));
        fixed(go, 42);
        card.add(go);

        add(card);
    }

    /** The dashed box: "INVITE CODE", the code with spaces between the letters, and Copy. */
    private static JPanel codeBox(String code) {
        JPanel box = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Shape r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 18, 18);
                g2.setColor(new Color(255, 255, 255, 10));
                g2.fill(r);
                g2.setColor(new Color(232, 217, 205, 90));
                g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f,
                        new float[] {6f, 5f}, 0f));
                g2.draw(r);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.setBorder(new EmptyBorder(14, 18, 14, 14));

        JPanel text = vertical();
        text.add(label("INVITE CODE", Font.BOLD, 11, Theme.AMBER));
        text.add(Box.createVerticalStrut(2));
        text.add(label(String.join(" ", code.split("")), Font.BOLD, 30, Theme.TEXT));
        box.add(text, BorderLayout.CENTER);

        RoundedButton copy = RoundedButton.outline("Copy");
        copy.setArc(RoundedButton.PILL);
        copy.setFont(Theme.font(Font.BOLD, 13));
        copy.setPreferredSize(new Dimension(84, 36));
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
            copy.setText("Copied");
        });
        JPanel copyHolder = new JPanel(new GridBagLayout()); // keeps the button its own size, centered
        copyHolder.setOpaque(false);
        copyHolder.add(copy);
        box.add(copyHolder, BorderLayout.EAST);
        return box;
    }

    /** Go to Home, the X and Esc all end here. */
    @Override
    public void close() {
        super.close();
        if (!finished) {
            finished = true;
            onDone.run();
        }
    }

    private static void fixed(JComponent c, int height) {
        Dimension d = new Dimension(FORM_WIDTH, height);
        c.setPreferredSize(d);
        c.setMinimumSize(d);
        c.setMaximumSize(d);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
}
