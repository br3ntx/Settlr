package com.settlr.ui.screens;

import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.model.TaskAssignment;
import com.settlr.model.User;
import com.settlr.service.SwapService;
import com.settlr.ui.ModalPopup;
import com.settlr.ui.components.AvatarLabel;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.Ui;
import com.settlr.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * "Ask to swap": pick a housemate to take your turn of a task. Each person shows how many tasks
 * they still have this week, so it's easy to pick someone who isn't swamped. The task stays
 * yours until they accept (SwapService).
 *
 *     SwapDialog.open(this, assignment, otherMembers, tasksLeftByUserId, name -> reload());
 */
public class SwapDialog extends ModalPopup {

    private static final int FORM_WIDTH = 380;

    private final SwapService swapService = new SwapService();
    private final List<PersonOption> options = new ArrayList<>();
    private final JLabel errorLabel = new JLabel(" ");
    private final RoundedButton send = new RoundedButton("Send request");
    private User chosen;

    public static void open(Component over, TaskAssignment a, List<User> others, Map<Integer, Integer> tasksLeft,
                            Consumer<String> onSent) {
        new SwapDialog(a, others, tasksLeft, onSent).showOver(over);
    }

    private SwapDialog(TaskAssignment a, List<User> others, Map<Integer, Integer> tasksLeft, Consumer<String> onSent) {
        GlassCard card = new GlassCard(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(22, 26, 20, 26));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(Ui.label("Ask to swap", Font.BOLD, 20, Theme.TEXT), BorderLayout.CENTER);
        JPanel closeHolder = new JPanel(new BorderLayout());
        closeHolder.setOpaque(false);
        closeHolder.add(createCloseButton(), BorderLayout.NORTH);
        header.add(closeHolder, BorderLayout.EAST);
        row(card, header, 0, 30);
        row(card, Ui.label(a.getTaskTitle() + TasksScreen.DOT + TasksScreen.when(a.getDueAt())
                + TasksScreen.DOT + TasksScreen.points(a.getTaskPoints()), Font.PLAIN, 12, Theme.MUTED_TEXT), 4, 16);
        row(card, Ui.label("Who should take it?", Font.BOLD, 12, Theme.PRIMARY), 16, 16);

        if (others.isEmpty()) {
            row(card, Ui.label("There's nobody else in your household yet.", Font.PLAIN, 13, Theme.TEXT),
                    10, 20);
        }
        for (User u : others) {
            int left = tasksLeft.getOrDefault(u.getId(), 0);
            PersonOption option = new PersonOption(u, left == 0 ? "No tasks left this week"
                    : left + (left == 1 ? " task" : " tasks") + " left this week");
            options.add(option);
            row(card, option, 8, 54);
        }
        JLabel hint = Ui.label(" ", Font.PLAIN, 11, Theme.MUTED_TEXT);
        row(card, hint, 8, 16);

        errorLabel.setFont(Theme.font(Font.PLAIN, 12));
        errorLabel.setForeground(Theme.DANGER);
        row(card, errorLabel, 2, 16);

        RoundedButton cancel = RoundedButton.outline("Cancel");
        cancel.setArc(RoundedButton.PILL);
        cancel.setFont(Theme.font(Font.BOLD, 14));
        cancel.addActionListener(e -> close());
        send.setArc(RoundedButton.PILL);
        send.setFont(Theme.font(Font.BOLD, 14));
        send.setEnabled(false); // until someone is picked
        send.addActionListener(e -> {
            errorLabel.setText(" ");
            send.setEnabled(false);
            try {
                swapService.request(a.getId(), chosen.getId());
            } catch (DataAccessException ex) {
                errorLabel.setText("Can't save right now. Is MySQL running?");
                send.setEnabled(true);
                return;
            } catch (SettlrException ex) {
                errorLabel.setText(ex.getMessage());
                send.setEnabled(true);
                return;
            }
            close();
            onSent.accept(chosen.getDisplayName());
        });
        JPanel buttons = new JPanel(new GridBagLayout());
        buttons.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 14);
        buttons.add(cancel, c);
        c.weightx = 1.4;
        c.insets = new Insets(0, 0, 0, 0);
        buttons.add(send, c);
        row(card, buttons, 6, 42);

        for (PersonOption o : options) {
            o.onPick = () -> {
                chosen = o.user;
                options.forEach(x -> x.setPicked(x == o));
                hint.setText(o.user.getDisplayName() + " gets the points if they accept. It stays yours until then.");
                send.setEnabled(true);
            };
        }
        add(card);
    }

    private void row(JPanel card, JComponent comp, int spaceAbove, int height) {
        if (spaceAbove > 0) {
            card.add(Box.createVerticalStrut(spaceAbove));
        }
        Dimension d = new Dimension(FORM_WIDTH, height);
        comp.setPreferredSize(d);
        comp.setMinimumSize(d);
        comp.setMaximumSize(d);
        comp.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(comp);
    }

    /** One housemate to pick: avatar, name, tasks left, and a radio dot. */
    private static final class PersonOption extends JPanel {
        final User user;
        Runnable onPick;
        private boolean picked;
        private boolean hover;

        PersonOption(User user, String detail) {
            super(new BorderLayout(12, 0));
            this.user = user;
            setOpaque(false);
            setBorder(new EmptyBorder(8, 14, 8, 16));
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(Ui.left(Ui.label(user.getDisplayName(), Font.BOLD, 14, Theme.TEXT)));
            text.add(Ui.left(Ui.label(detail, Font.PLAIN, 11, Theme.MUTED_TEXT)));
            JPanel textHolder = new JPanel(new GridBagLayout());
            textHolder.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.weightx = 1;
            gc.anchor = GridBagConstraints.WEST;
            textHolder.add(text, gc);
            JPanel avatarHolder = new JPanel(new GridBagLayout());
            avatarHolder.setOpaque(false);
            avatarHolder.add(new AvatarLabel(user, 34));
            add(avatarHolder, BorderLayout.WEST);
            add(textHolder, BorderLayout.CENTER);
            add(Box.createHorizontalStrut(20), BorderLayout.EAST); // room for the radio dot
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { if (onPick != null) onPick.run(); }
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        void setPicked(boolean picked) {
            this.picked = picked;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape box = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 14, 14);
            g2.setColor(picked ? new Color(232, 217, 205, 26) : new Color(255, 255, 255, hover ? 14 : 7));
            g2.fill(box);
            g2.setColor(picked ? Theme.PRIMARY : new Color(255, 255, 255, 50));
            g2.setStroke(new BasicStroke(picked ? 1.6f : 1f));
            g2.draw(box);
            // radio dot on the right
            int d = 18, x = getWidth() - 16 - d, y = (getHeight() - d) / 2;
            if (picked) {
                g2.setColor(Theme.PRIMARY);
                g2.fill(new Ellipse2D.Float(x, y, d, d));
                g2.setColor(Theme.ON_PRIMARY);
                g2.fill(new Ellipse2D.Float(x + 5.5f, y + 5.5f, 7, 7));
            } else {
                g2.setColor(new Color(232, 217, 205, 130));
                g2.setStroke(new BasicStroke(1.8f));
                g2.draw(new Ellipse2D.Float(x + 1, y + 1, d - 2, d - 2));
            }
            g2.dispose();
        }
    }
}
