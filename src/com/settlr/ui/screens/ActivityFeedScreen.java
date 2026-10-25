package com.settlr.ui.screens;

import com.settlr.exception.SettlrException;
import com.settlr.model.ActivityLog;
import com.settlr.model.ActivityType;
import com.settlr.service.ActivityService;
import com.settlr.ui.BaseScreen;
import com.settlr.ui.theme.Theme;
import com.settlr.util.DateTimeUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The Activity screen: the newest 50 things that happened in the household, newest first,
 * each with a colored dot and a time like "2h ago".
 *
 * <p>The screen only shows what ActivityService returns. The sentence of each event was written
 * when it happened ("Ana completed Wash Dishes (2 hours late)"), so nothing is rebuilt here.
 * It extends BaseScreen, so the Navigator calls onShow() every time it appears.
 */
public class ActivityFeedScreen extends BaseScreen {

    /** TEMP: true while DBConnection/Session/etc. are not merged yet. Set to false to use the real service. */
    private static final boolean USE_SAMPLE_DATA = true;

    /** Master doc 3.8: the feed shows the latest 50 events. */
    private static final int FEED_SIZE = 50;
    private static final String CARD_LIST = "list";
    private static final String CARD_EMPTY = "empty";

    private final ActivityService service = new ActivityService();

    private final JLabel errorLabel = new JLabel(" ");
    private final JPanel listPanel = new JPanel();
    private final CardLayout cards = new CardLayout();
    private final JPanel cardHolder = new JPanel(cards);

    public ActivityFeedScreen() {
        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));

        add(buildHeader(), BorderLayout.NORTH);

        cardHolder.setOpaque(false);
        cardHolder.add(buildListCard(), CARD_LIST);
        cardHolder.add(buildEmptyCard(), CARD_EMPTY);
        add(cardHolder, BorderLayout.CENTER);
    }

    /** Called every time the screen appears, so the feed is always fresh. */
    @Override
    public void onShow() {
        showError(null);
        List<ActivityLog> feed = new ArrayList<>();
        try {
            feed = loadFeed();
        } catch (SettlrException ex) {
            showError(ex.getMessage());
        }
        showFeed(feed);
    }

    private List<ActivityLog> loadFeed() throws SettlrException {
        if (USE_SAMPLE_DATA) {
            return loadSampleData();
        }
        return service.getFeed(FEED_SIZE);
    }

    // ------------------------------------------------------------------ building the layout

    private JPanel buildHeader() {
        JLabel title = new JLabel("Activity");
        title.setFont(Theme.font(Font.BOLD, 28));
        title.setForeground(Theme.TEXT);

        JLabel subtitle = new JLabel("Ano'ng ganap sa bahay? The latest " + FEED_SIZE + " events.");
        subtitle.setFont(Theme.font(Font.PLAIN, 14));
        subtitle.setForeground(Theme.MUTED_TEXT);

        errorLabel.setFont(Theme.font(Font.PLAIN, 13));
        errorLabel.setForeground(Theme.DANGER);

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.add(title);
        box.add(Box.createVerticalStrut(4));
        box.add(subtitle);
        box.add(Box.createVerticalStrut(8));
        box.add(errorLabel);
        return box;
    }

    private JPanel buildListCard() {
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(Theme.SURFACE);

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel card = whiteCard(new BorderLayout());
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildEmptyCard() {
        JLabel title = new JLabel("Wala pang ganap dito", SwingConstants.CENTER);
        title.setFont(Theme.font(Font.BOLD, 22));
        title.setForeground(Theme.TEXT);
        title.setAlignmentX(CENTER_ALIGNMENT);

        JLabel body = new JLabel("No activity yet. Finish a chore or add an expense and it will show up here!",
                SwingConstants.CENTER);
        body.setFont(Theme.font(Font.PLAIN, 14));
        body.setForeground(Theme.MUTED_TEXT);
        body.setAlignmentX(CENTER_ALIGNMENT);

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.add(title);
        box.add(Box.createVerticalStrut(8));
        box.add(body);

        JPanel card = whiteCard(new GridBagLayout());
        card.add(box);
        return card;
    }

    /** A white card with a 1 px BORDER outline. Replace with CardPanel when M6 builds it. */
    private static JPanel whiteCard(java.awt.LayoutManager layout) {
        JPanel card = new JPanel(layout);
        card.setBackground(Theme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                BorderFactory.createEmptyBorder(12, 24, 12, 24)));
        return card;
    }

    // ------------------------------------------------------------------ showing the feed

    private void showFeed(List<ActivityLog> feed) {
        if (feed.isEmpty()) {
            cards.show(cardHolder, CARD_EMPTY);
            return;
        }
        listPanel.removeAll();
        for (ActivityLog log : feed) {
            JPanel row = new FeedRow(log);
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            listPanel.add(row);
        }
        listPanel.revalidate();
        listPanel.repaint();
        cards.show(cardHolder, CARD_LIST);
    }

    /** Friendly message only; null clears it. Never a stack trace or SQL. */
    private void showError(String message) {
        errorLabel.setText(message == null || message.isBlank() ? " " : message);
    }

    /** Same colors as the status badges elsewhere: done = teal, late = orange, missed = red. */
    private static Color colorFor(ActivityType type) {
        return switch (type) {
            case TASK_DONE, PAYMENT_CONFIRMED, SWAP_ACCEPTED -> Theme.PRIMARY;
            case TASK_LATE -> Theme.WARNING;
            case TASK_MISSED -> Theme.DANGER;
            case EXPENSE_ADDED -> Theme.PRIMARY_DARK;
            case MEMBER_JOINED -> Theme.MUTED_TEXT;
        };
    }

    // ------------------------------------------------------------------ sample data

    /** TEMP sample data for previewing before the backend is merged. Delete with USE_SAMPLE_DATA. */
    private List<ActivityLog> loadSampleData() {
        LocalDateTime now = LocalDateTime.now();
        List<ActivityLog> sample = new ArrayList<>();
        sample.add(new ActivityLog(8, 1, 3, ActivityType.PAYMENT_CONFIRMED, "Ben confirmed Cora's payment of \u20b1400.00", now.minusMinutes(12)));
        sample.add(new ActivityLog(7, 1, 1, ActivityType.TASK_DONE, "Ana completed Clean Bathroom", now.minusHours(2)));
        sample.add(new ActivityLog(6, 1, 2, ActivityType.TASK_LATE, "Ben completed Take Out Trash (6 hours late)", now.minusHours(5)));
        sample.add(new ActivityLog(5, 1, 3, ActivityType.EXPENSE_ADDED, "Cora added Groceries (\u20b1900.00)", now.minusHours(9)));
        sample.add(new ActivityLog(4, 1, 5, ActivityType.SWAP_ACCEPTED, "Ella took over Wash Dishes from Ana", now.minusDays(1)));
        sample.add(new ActivityLog(3, 1, 4, ActivityType.TASK_MISSED, "Dodong missed Sweep Floor", now.minusDays(2)));
        sample.add(new ActivityLog(2, 1, 5, ActivityType.MEMBER_JOINED, "Ella joined the household", now.minusDays(6)));
        return sample;
    }

    // ------------------------------------------------------------------ small drawing helpers

    /** One event: colored dot, the sentence, and the time on the right. */
    private static class FeedRow extends JPanel {
        FeedRow(ActivityLog log) {
            setLayout(new BorderLayout(12, 0));
            setOpaque(false);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    BorderFactory.createEmptyBorder(14, 4, 14, 4)));

            JLabel message = new JLabel(log.getMessage());
            message.setFont(Theme.font(Font.PLAIN, 14));
            message.setForeground(Theme.TEXT);

            String when = log.getCreatedAt() == null ? "" : DateTimeUtil.timeAgo(log.getCreatedAt());
            JLabel time = new JLabel(when);
            time.setFont(Theme.font(Font.PLAIN, 12));
            time.setForeground(Theme.MUTED_TEXT);

            JPanel dotHolder = new JPanel(new GridBagLayout()); // GridBag centers the dot vertically
            dotHolder.setOpaque(false);
            dotHolder.add(new Dot(colorFor(log.getType())));

            add(dotHolder, BorderLayout.WEST);
            add(message, BorderLayout.CENTER);
            add(time, BorderLayout.EAST);
        }
    }

    /** Small round dot that shows the kind of event at a glance. */
    private static class Dot extends JComponent {
        private final Color color;

        Dot(Color color) {
            this.color = color;
            setPreferredSize(new Dimension(12, 12));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(1, 1, 10, 10);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ TEMP preview

    /** TEMP preview: run this class to see the screen. Delete before opening the pull request. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Activity preview");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            ActivityFeedScreen screen = new ActivityFeedScreen();
            frame.setContentPane(screen);
            frame.setSize(1100, 700);
            frame.setMinimumSize(new Dimension(800, 600));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            screen.onShow();
        });
    }
}
