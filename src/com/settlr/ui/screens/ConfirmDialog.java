package com.settlr.ui.screens;

import com.settlr.model.User;
import com.settlr.ui.ModalPopup;
import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

import static com.settlr.ui.components.Ui.*;

/**
 * A small "are you sure?" popup in the app's style, used instead of the plain Swing
 * JOptionPane. Optional avatar, a short line, an optional table of names and amounts,
 * and two buttons (the main one can be red for things like removing someone).
 *
 *     new ConfirmDialog("Remove Dani?", null)
 *             .avatar(dani)
 *             .buttons("Cancel", "Remove", true, () -> remove(dani))
 *             .showOver(this);
 */
public class ConfirmDialog extends ModalPopup {

    private static final int FORM_WIDTH = 368;

    private final GlassCard card = new GlassCard(null);
    private final JPanel header = new JPanel(new BorderLayout(12, 0));
    private final JPanel body = new JPanel();
    private final JPanel buttons = new JPanel(new GridBagLayout());

    public ConfirmDialog(String title, String line) {
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 26, 20, 26));

        header.setOpaque(false);
        header.add(label(title, Font.BOLD, 20, Theme.TEXT), BorderLayout.CENTER);
        JPanel closeHolder = new JPanel(new BorderLayout());
        closeHolder.setOpaque(false);
        closeHolder.add(createCloseButton(), BorderLayout.NORTH);
        header.add(closeHolder, BorderLayout.EAST);
        fixed(header, 42);
        card.add(header);

        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (line != null) {
            JLabel l = label("<html>" + line + "</html>", Font.PLAIN, 12, Theme.MUTED_TEXT);
            fixed(l, wrappedHeight(l));
            body.add(Box.createVerticalStrut(4));
            body.add(l);
        }
        card.add(body);

        buttons.setOpaque(false);
        card.add(Box.createVerticalStrut(20));
        fixed(buttons, 42);
        card.add(buttons);
        add(card);
    }

    /** Puts the person's avatar next to the title. */
    public ConfirmDialog avatar(User user) {
        header.add(new AvatarLabel(user, 40), BorderLayout.WEST);
        return this;
    }

    /**
     * A box of rows like "Ella   P340.00", with a line and a bold total row under them.
     * rows are {name, amount} pairs; totalLabel may be null for no total.
     */
    public ConfirmDialog table(String[][] rows, String totalLabel, String total, Color amountColor) {
        JPanel box = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Shape r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 14, 14);
                g2.setColor(new Color(255, 255, 255, 10));
                g2.fill(r);
                g2.setColor(new Color(255, 255, 255, 55));
                g2.draw(r);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(new EmptyBorder(6, 16, 6, 16));
        for (String[] row : rows) {
            box.add(line(row[0], row[1], Font.PLAIN, amountColor));
        }
        if (totalLabel != null) {
            JPanel rule = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(new Color(232, 217, 205, 60));
                    g.fillRect(0, getHeight() / 2, getWidth(), 1);
                }
            };
            rule.setOpaque(false);
            rule.setPreferredSize(new Dimension(10, 11));
            rule.setMaximumSize(new Dimension(Integer.MAX_VALUE, 11));
            rule.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.add(rule);
            box.add(line(totalLabel, total, Font.BOLD, amountColor));
        }
        body.add(Box.createVerticalStrut(14));
        fixed(box, box.getPreferredSize().height);
        body.add(box);
        return this;
    }

    /** The two buttons. onConfirm runs after the popup closes. danger makes the main button red. */
    public ConfirmDialog buttons(String cancelText, String confirmText, boolean danger, Runnable onConfirm) {
        RoundedButton cancel = RoundedButton.outline(cancelText);
        cancel.setArc(RoundedButton.PILL);
        cancel.setFont(Theme.font(Font.BOLD, 14));
        cancel.addActionListener(e -> close());
        RoundedButton ok = new RoundedButton(confirmText);
        ok.setArc(RoundedButton.PILL);
        ok.setFont(Theme.font(Font.BOLD, 14));
        if (danger) {
            ok.setColors(Theme.DANGER, Theme.DANGER.darker(), Color.WHITE);
        }
        ok.addActionListener(e -> {
            close();
            onConfirm.run();
        });
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 14);
        buttons.add(cancel, c);
        c.weightx = 1.4;
        c.insets = new Insets(0, 0, 0, 0);
        buttons.add(ok, c);
        return this;
    }

    /** Just one button that closes the popup (for "you can't do that" messages). */
    public ConfirmDialog okOnly(String text) {
        RoundedButton ok = RoundedButton.outline(text);
        ok.setArc(RoundedButton.PILL);
        ok.setFont(Theme.font(Font.BOLD, 14));
        ok.addActionListener(e -> close());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 1;
        buttons.add(ok, c);
        return this;
    }

    // ---------- helpers ----------

    private static JPanel line(String name, String amount, int style, Color amountColor) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(6, 0, 6, 0));
        p.add(label(name, style, 13, Theme.TEXT), BorderLayout.WEST);
        p.add(label(amount, Font.BOLD, 13, amountColor), BorderLayout.EAST);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private static int wrappedHeight(JLabel htmlLabel) {
        View view = (View) htmlLabel.getClientProperty(BasicHTML.propertyKey);
        if (view == null) {
            return htmlLabel.getPreferredSize().height;
        }
        view.setSize(FORM_WIDTH, 0);
        return (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
    }

    private static void fixed(JComponent c, int height) {
        Dimension d = new Dimension(FORM_WIDTH, height);
        c.setPreferredSize(d);
        c.setMinimumSize(d);
        c.setMaximumSize(d);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

}
