package com.settlr.ui.screens;

import com.settlr.exception.SettlrException;
import com.settlr.model.LeaderboardEntry;
import com.settlr.ranking.RankingCategory;
import com.settlr.ranking.RankingPeriod;
import com.settlr.service.LeaderboardService;
import com.settlr.ui.theme.Theme;
import com.settlr.util.MoneyUtil;

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
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleFunction;

/**
 * The Leaderboard screen: Week / Month / All Time tabs, the list of rankings on the left, and on
 * the right a podium for the top 3 plus the full list of members.
 *
 * <p>The screen never touches SQL or decides who wins: it asks LeaderboardService and draws the
 * answer. Gold (Theme.ACCENT) is used only for rank #1, weekly titles and the podium.
 * It extends JPanel for now; when BaseScreen exists, change the parent and keep onShow().
 */
public class LeaderboardScreen extends JPanel {

    /** TEMP: true while DBConnection/Session/etc. are not merged yet. Set to false to use the real service. */
    private static final boolean USE_SAMPLE_DATA = true;

    private static final int LIST_WIDTH = 300;
    private static final String CARD_CONTENT = "content";
    private static final String CARD_EMPTY = "empty";

    private final LeaderboardService service = new LeaderboardService();

    private List<RankingCategory> categories = new ArrayList<>();
    private Map<Integer, List<String>> weeklyTitles = new HashMap<>();
    private final Map<String, List<LeaderboardEntry>> sampleEntries = new HashMap<>();
    private RankingPeriod selectedPeriod = RankingPeriod.WEEK;
    private RankingCategory selectedCategory;

    private final Map<RankingPeriod, JLabel> tabs = new EnumMap<>(RankingPeriod.class);
    private final JPanel categoryListPanel = new JPanel();
    private final JLabel errorLabel = new JLabel(" ");
    private final JLabel rankingTitleLabel = new JLabel();
    private final JLabel rankingDescLabel = new JLabel();
    private final PodiumPanel podiumPanel = new PodiumPanel();
    private final JLabel noLeaderLabel = new JLabel();
    private final JPanel entryListPanel = new JPanel();
    private final CardLayout rightCards = new CardLayout();
    private final JPanel rightPanel = new JPanel(rightCards);
    private final JLabel emptyTitleLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel emptyBodyLabel = new JLabel("", SwingConstants.CENTER);

    public LeaderboardScreen() {
        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        styleTabs();
    }

    /** Called every time the screen appears, so the numbers are always fresh. */
    public void onShow() {
        showError(null);
        try {
            loadCategoriesAndTitles();
            keepOrPickCategory();
            rebuildCategoryList();
            showSelectedRanking();
        } catch (SettlrException ex) {
            showError(ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ building the layout

    private JPanel buildHeader() {
        JLabel title = new JLabel("Leaderboard");
        title.setFont(Theme.font(Font.BOLD, 28));
        title.setForeground(Theme.TEXT);

        JLabel subtitle = new JLabel("Sino ang pinaka-masipag? Check the ranks!");
        subtitle.setFont(Theme.font(Font.PLAIN, 14));
        subtitle.setForeground(Theme.MUTED_TEXT);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(subtitle);

        JPanel tabBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 24, 0));
        tabBar.setOpaque(false);
        for (RankingPeriod period : RankingPeriod.values()) {
            JLabel tab = new JLabel(period.getLabel());
            tab.setFont(Theme.font(Font.BOLD, 14));
            tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            tab.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectPeriod(period);
                }
            });
            tabs.put(period, tab);
            tabBar.add(tab);
        }

        errorLabel.setFont(Theme.font(Font.PLAIN, 13));
        errorLabel.setForeground(Theme.DANGER);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(titleBox, BorderLayout.WEST);
        top.add(tabBar, BorderLayout.EAST);

        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setOpaque(false);
        header.add(top, BorderLayout.NORTH);
        header.add(errorLabel, BorderLayout.SOUTH);
        return header;
    }

    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(24, 0));
        body.setOpaque(false);
        body.add(buildLeftCard(), BorderLayout.WEST);

        rightPanel.setOpaque(false);
        rightPanel.add(buildContentCard(), CARD_CONTENT);
        rightPanel.add(buildEmptyCard(), CARD_EMPTY);
        body.add(rightPanel, BorderLayout.CENTER);
        return body;
    }

    private JPanel buildLeftCard() {
        JLabel heading = new JLabel("Rankings");
        heading.setFont(Theme.font(Font.BOLD, 18));
        heading.setForeground(Theme.TEXT);
        heading.setBorder(BorderFactory.createEmptyBorder(0, 4, 12, 0));

        categoryListPanel.setLayout(new BoxLayout(categoryListPanel, BoxLayout.Y_AXIS));
        categoryListPanel.setBackground(Theme.SURFACE);

        JScrollPane scroll = new JScrollPane(categoryListPanel);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel card = whiteCard(new BorderLayout());
        card.setPreferredSize(new Dimension(LIST_WIDTH, 0));
        card.add(heading, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildContentCard() {
        rankingTitleLabel.setFont(Theme.font(Font.BOLD, 22));
        rankingTitleLabel.setForeground(Theme.TEXT);
        rankingDescLabel.setFont(Theme.font(Font.PLAIN, 14));
        rankingDescLabel.setForeground(Theme.MUTED_TEXT);

        JPanel headBox = new JPanel();
        headBox.setOpaque(false);
        headBox.setLayout(new BoxLayout(headBox, BoxLayout.Y_AXIS));
        headBox.add(rankingTitleLabel);
        headBox.add(Box.createVerticalStrut(4));
        headBox.add(rankingDescLabel);

        noLeaderLabel.setFont(Theme.font(Font.PLAIN, 14));
        noLeaderLabel.setForeground(Theme.MUTED_TEXT);
        noLeaderLabel.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 0));

        JPanel topBox = new JPanel(new BorderLayout());
        topBox.setOpaque(false);
        topBox.add(headBox, BorderLayout.NORTH);
        topBox.add(podiumPanel, BorderLayout.CENTER);
        topBox.add(noLeaderLabel, BorderLayout.SOUTH);

        entryListPanel.setLayout(new BoxLayout(entryListPanel, BoxLayout.Y_AXIS));
        entryListPanel.setBackground(Theme.SURFACE);

        JScrollPane scroll = new JScrollPane(entryListPanel);
        scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        scroll.getViewport().setBackground(Theme.SURFACE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel card = whiteCard(new BorderLayout(0, 8));
        card.add(topBox, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildEmptyCard() {
        emptyTitleLabel.setFont(Theme.font(Font.BOLD, 22));
        emptyTitleLabel.setForeground(Theme.TEXT);
        emptyBodyLabel.setFont(Theme.font(Font.PLAIN, 14));
        emptyBodyLabel.setForeground(Theme.MUTED_TEXT);

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        emptyTitleLabel.setAlignmentX(CENTER_ALIGNMENT);
        emptyBodyLabel.setAlignmentX(CENTER_ALIGNMENT);
        box.add(emptyTitleLabel);
        box.add(Box.createVerticalStrut(8));
        box.add(emptyBodyLabel);

        JPanel card = whiteCard(new GridBagLayout());
        card.add(box);
        return card;
    }

    /** A white rounded-look card (1 px BORDER outline). Replace with CardPanel when M6 builds it. */
    private static JPanel whiteCard(java.awt.LayoutManager layout) {
        JPanel card = new JPanel(layout);
        card.setBackground(Theme.SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1),
                BorderFactory.createEmptyBorder(20, 24, 20, 24)));
        return card;
    }

    // ------------------------------------------------------------------ loading data

    private void loadCategoriesAndTitles() throws SettlrException {
        if (USE_SAMPLE_DATA) {
            loadSampleData();
        } else {
            categories = service.getCategories();
            weeklyTitles = service.getWeeklyTitles();
        }
    }

    private List<LeaderboardEntry> fetchEntries() throws SettlrException {
        if (USE_SAMPLE_DATA) {
            return sampleEntries.getOrDefault(selectedCategory.getClass().getSimpleName(), List.of());
        }
        return service.getLeaderboard(selectedCategory, selectedPeriod);
    }

    /** Rankings that make sense on the current tab (MostImproved is hidden on All Time). */
    private List<RankingCategory> visibleCategories() {
        List<RankingCategory> visible = new ArrayList<>();
        for (RankingCategory category : categories) {
            if (category.isAvailableFor(selectedPeriod)) {
                visible.add(category);
            }
        }
        return visible;
    }

    /** Keeps the user's current ranking if the tab still offers it, otherwise picks the first one. */
    private void keepOrPickCategory() {
        List<RankingCategory> visible = visibleCategories();
        boolean stillThere = false;
        for (RankingCategory category : visible) {
            if (selectedCategory != null && category.getClass() == selectedCategory.getClass()) {
                selectedCategory = category;
                stillThere = true;
            }
        }
        if (!stillThere) {
            selectedCategory = visible.isEmpty() ? null : visible.get(0);
        }
    }

    // ------------------------------------------------------------------ reacting to clicks

    private void selectPeriod(RankingPeriod period) {
        selectedPeriod = period;
        styleTabs();
        showError(null);
        keepOrPickCategory();
        rebuildCategoryList();
        showSelectedRanking();
    }

    private void selectCategory(RankingCategory category) {
        selectedCategory = category;
        showError(null);
        rebuildCategoryList();
        showSelectedRanking();
    }

    // ------------------------------------------------------------------ drawing the screen

    private void styleTabs() {
        for (Map.Entry<RankingPeriod, JLabel> e : tabs.entrySet()) {
            boolean selected = e.getKey() == selectedPeriod;
            JLabel tab = e.getValue();
            tab.setForeground(selected ? Theme.PRIMARY : Theme.MUTED_TEXT);
            tab.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 3, 0, selected ? Theme.PRIMARY : Theme.BACKGROUND),
                    BorderFactory.createEmptyBorder(4, 2, 4, 2)));
        }
    }

    private void rebuildCategoryList() {
        categoryListPanel.removeAll();
        for (RankingCategory category : visibleCategories()) {
            JPanel row = new RankingRow(category, category == selectedCategory);
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            categoryListPanel.add(row);
            categoryListPanel.add(Box.createVerticalStrut(6));
        }
        categoryListPanel.revalidate();
        categoryListPanel.repaint();
    }

    private void showSelectedRanking() {
        if (selectedCategory == null) {
            showEmpty("Wala pang rankings", "No rankings to show right now.");
            return;
        }

        List<LeaderboardEntry> entries = new ArrayList<>();
        try {
            entries = fetchEntries();
        } catch (SettlrException ex) {
            showError(ex.getMessage());
        }
        if (entries.isEmpty()) {
            showEmpty("Wala pang laman dito",
                    "Nobody is on this ranking for " + selectedPeriod.getLabel().toLowerCase()
                            + " yet. Finish a chore or settle a bill, then check back!");
            return;
        }

        rankingTitleLabel.setText(selectedCategory.getTitle());
        rankingDescLabel.setText(selectedCategory.getDescription());

        // If the top value is 0 nobody is really leading, so no podium, no gold and no titles are shown.
        boolean hasLeader = entries.get(0).getValue() != 0;
        podiumPanel.setEntries(hasLeader ? entries : new ArrayList<>());
        podiumPanel.setVisible(hasLeader);
        noLeaderLabel.setText("Wala pang nangunguna rito. Be the first!");
        noLeaderLabel.setVisible(!hasLeader);

        entryListPanel.removeAll();
        for (LeaderboardEntry entry : entries) {
            JPanel row = new EntryRow(entry, hasLeader && entry.getRank() == 1,
                    weeklyTitles.getOrDefault(entry.getUserId(), List.of()));
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            entryListPanel.add(row);
        }
        entryListPanel.revalidate();
        entryListPanel.repaint();
        rightCards.show(rightPanel, CARD_CONTENT);
    }

    private void showEmpty(String title, String body) {
        emptyTitleLabel.setText(title);
        emptyBodyLabel.setText(body);
        rightCards.show(rightPanel, CARD_EMPTY);
    }

    /** Friendly message only; null clears it. Never a stack trace or SQL. */
    private void showError(String message) {
        errorLabel.setText(message == null || message.isBlank() ? " " : message);
    }

    // ------------------------------------------------------------------ sample data

    /**
     * TEMP sample data so the screen can be previewed before the backend is merged. Numbers are
     * invented; user ids 1 to 5 are Ana, Ben, Cora, Dodong and Ella. Delete with USE_SAMPLE_DATA.
     */
    private void loadSampleData() {
        categories = service.getAllCategories();
        weeklyTitles = new HashMap<>();
        weeklyTitles.put(1, List.of("Settlr Score", "Chore Champion"));
        weeklyTitles.put(2, List.of("\"Mamaya Na\" Award"));
        weeklyTitles.put(3, List.of("Sponsor"));
        weeklyTitles.put(4, List.of("Ghost", "Utang King/Queen"));

        String[] names = {"Ana", "Ben", "Cora", "Dodong", "Ella"};
        DoubleFunction<String> pts = v -> Math.round(v) + " pts";
        DoubleFunction<String> peso = v -> MoneyUtil.format(BigDecimal.valueOf(v).setScale(2));
        DoubleFunction<String> count = v -> String.valueOf(Math.round(v));

        sampleEntries.clear();
        sampleEntries.put("SettlrScore", sample(names, pts, 24, 17, 12, 5, -3));
        sampleEntries.put("ChoreChampion", sample(names, pts, 18, 14, 14, 6, 0));
        sampleEntries.put("EarlyBird", sample(names, v -> count.apply(v) + " tasks", 4, 2, 1, 0, 0));
        sampleEntries.put("BayadAgad", sample(names, v -> Math.round(v) + "%", 100, 90, 75, 50)); // Ella: no payments, not ranked
        sampleEntries.put("Sponsor", sample(names, peso, 6000, 1200, 900, 0, 0));
        sampleEntries.put("Hero", sample(names, v -> count.apply(v) + " swaps", 3, 1, 0, 0, 0));
        sampleEntries.put("Streak", sample(names, v -> count.apply(v) + " weeks", 3, 0, 1, 2, 1));
        sampleEntries.put("MostImproved", sample(names, v -> (v > 0 ? "+" : "") + Math.round(v) + " pts", 7, -1, -4, -7, -2));
        sampleEntries.put("MamayaNa", sample(names, v -> count.apply(v) + " late tasks", 0, 4, 1, 2, 1));
        sampleEntries.put("Ghost", sample(names, v -> count.apply(v) + " missed tasks", 0, 0, 1, 3, 0));
        sampleEntries.put("UtangKing", sample(names, peso, 0, 0, 400, 1800, 300));
        sampleEntries.put("SlowSettler", sample(names, v -> v <= 0 ? "\u2014" : v + " days", 14.6, 13.0, 15.1, 0, 0));
        sampleEntries.put("Dodger", sample(names, v -> count.apply(v) + " requests", 1, 2, 0, 0, 1));
    }

    /** Turns sample numbers into ranked entries (best first, ties A to Z, shared ranks like SQL RANK). */
    private static List<LeaderboardEntry> sample(String[] names, DoubleFunction<String> label, double... values) {
        List<LeaderboardEntry> list = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            list.add(new LeaderboardEntry(i + 1, names[i], values[i], 1, label.apply(values[i])));
        }
        list.sort(Comparator.comparingDouble(LeaderboardEntry::getValue).reversed()
                .thenComparing(LeaderboardEntry::getDisplayName));
        for (int i = 0; i < list.size(); i++) {
            boolean tied = i > 0 && list.get(i - 1).getValue() == list.get(i).getValue();
            list.get(i).setRank(tied ? list.get(i - 1).getRank() : i + 1);
        }
        return list;
    }

    // ------------------------------------------------------------------ small drawing helpers

    /** One clickable ranking in the left list. */
    private class RankingRow extends JPanel {
        RankingRow(RankingCategory category, boolean selected) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(selected ? Theme.BACKGROUND : Theme.SURFACE);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(selected ? Theme.PRIMARY : Theme.SURFACE, 2),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel title = new JLabel(category.getTitle());
            title.setFont(Theme.font(Font.BOLD, 14));
            title.setForeground(selected ? Theme.PRIMARY : Theme.TEXT);
            title.setAlignmentX(LEFT_ALIGNMENT);

            add(title);
            if (category.isShame()) {
                // a small hint so members know which rankings the Admin can hide
                JLabel tag = new JLabel("Shame ranking");
                tag.setFont(Theme.font(Font.PLAIN, 12));
                tag.setForeground(Theme.MUTED_TEXT);
                tag.setAlignmentX(LEFT_ALIGNMENT);
                add(Box.createVerticalStrut(2));
                add(tag);
            }

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selectCategory(category);
                }
            });
        }
    }

    /** One member in the full list: rank badge, name, weekly title pills, value. */
    private static class EntryRow extends JPanel {
        EntryRow(LeaderboardEntry entry, boolean gold, List<String> titles) {
            setLayout(new BorderLayout(12, 0));
            setOpaque(false);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    BorderFactory.createEmptyBorder(10, 4, 10, 4)));

            JLabel name = new JLabel(entry.getDisplayName());
            name.setFont(Theme.font(Font.BOLD, 14));
            name.setForeground(Theme.TEXT);

            JPanel nameBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            nameBox.setOpaque(false);
            nameBox.add(name);
            for (String title : titles) {
                nameBox.add(new TitlePill(title));
            }

            JLabel value = new JLabel(entry.getLabel());
            value.setFont(Theme.font(Font.BOLD, 14));
            value.setForeground(Theme.TEXT);

            add(new RankBadge(entry.getRank(), gold), BorderLayout.WEST);
            add(nameBox, BorderLayout.CENTER);
            add(value, BorderLayout.EAST);
        }
    }

    /** Round badge with the rank number. Gold only for #1. */
    private static class RankBadge extends JComponent {
        private final int rank;
        private final boolean gold;

        RankBadge(int rank, boolean gold) {
            this.rank = rank;
            this.gold = gold;
            setPreferredSize(new Dimension(32, 32));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(gold ? Theme.ACCENT : Theme.BORDER);
            g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            g2.setColor(Theme.TEXT);
            g2.setFont(Theme.font(Font.BOLD, 13));
            FontMetrics fm = g2.getFontMetrics();
            String text = String.valueOf(rank);
            g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Small gold pill with a weekly title next to a member's name. */
    private static class TitlePill extends JLabel {
        TitlePill(String text) {
            super(text);
            setFont(Theme.font(Font.BOLD, 12));
            setForeground(Theme.TEXT);
            setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
            setOpaque(false);
            setToolTipText("This week's title");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.ACCENT);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Draws the top 3 as podium blocks: 2nd on the left, 1st in the middle, 3rd on the right. */
    private static class PodiumPanel extends JPanel {
        private List<LeaderboardEntry> top = new ArrayList<>();

        PodiumPanel() {
            setOpaque(false);
            setPreferredSize(new Dimension(100, 205));
        }

        void setEntries(List<LeaderboardEntry> entries) {
            top = new ArrayList<>(entries.subList(0, Math.min(3, entries.size())));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (top.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int gap = 16;
            int slotWidth = Math.min(170, (getWidth() - 2 * gap) / 3);
            int startX = (getWidth() - (slotWidth * 3 + gap * 2)) / 2;
            int[] listIndexForSlot = {1, 0, 2};
            for (int slot = 0; slot < 3; slot++) {
                int index = listIndexForSlot[slot];
                if (index < top.size()) {
                    drawSlot(g2, top.get(index), startX + slot * (slotWidth + gap), slotWidth);
                }
            }
            g2.dispose();
        }

        private void drawSlot(Graphics2D g2, LeaderboardEntry entry, int x, int width) {
            int rank = entry.getRank();
            int blockHeight = rank == 1 ? 130 : rank == 2 ? 108 : 90;
            int bottom = getHeight() - 6;
            int blockTop = bottom - blockHeight;

            // avatar circle with initials sits on top of the block
            int avatar = 44;
            int avatarX = x + (width - avatar) / 2;
            int avatarY = blockTop - avatar - 6;
            g2.setColor(Theme.SURFACE);
            g2.fillOval(avatarX, avatarY, avatar, avatar);
            g2.setColor(Theme.BORDER);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(avatarX, avatarY, avatar, avatar);
            g2.setColor(Theme.TEXT);
            g2.setFont(Theme.font(Font.BOLD, 14));
            FontMetrics fm = g2.getFontMetrics();
            String initials = initialsOf(entry.getDisplayName());
            g2.drawString(initials, avatarX + (avatar - fm.stringWidth(initials)) / 2,
                    avatarY + (avatar - fm.getHeight()) / 2 + fm.getAscent());

            // the block: gold for #1, neutral for the others
            Color fill = rank == 1 ? Theme.ACCENT : rank == 2 ? Theme.BORDER : Theme.INPUT_BORDER;
            g2.setColor(fill);
            g2.fillRoundRect(x, blockTop, width, blockHeight, 16, 16);

            g2.setColor(Theme.TEXT);
            drawCentered(g2, "#" + rank, Theme.font(Font.BOLD, 20), x, width, blockTop + 28);
            drawCentered(g2, entry.getDisplayName(), Theme.font(Font.BOLD, 14), x, width, blockTop + 52);
            drawCentered(g2, entry.getLabel(), Theme.font(Font.PLAIN, 13), x, width, blockTop + 72);
        }

        private void drawCentered(Graphics2D g2, String text, Font font, int x, int width, int baseline) {
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics();
            String fitted = fit(text, fm, width - 12);
            g2.drawString(fitted, x + (width - fm.stringWidth(fitted)) / 2, baseline);
        }

        /** Shortens text with "..." so a long name never spills out of its block. */
        private static String fit(String text, FontMetrics fm, int maxWidth) {
            if (fm.stringWidth(text) <= maxWidth) {
                return text;
            }
            String cut = text;
            while (cut.length() > 1 && fm.stringWidth(cut + "...") > maxWidth) {
                cut = cut.substring(0, cut.length() - 1);
            }
            return cut + "...";
        }

        private static String initialsOf(String name) {
            String[] parts = name.trim().split("\\s+");
            String first = parts[0].substring(0, 1);
            String second = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
            return (first + second).toUpperCase();
        }
    }

    // ------------------------------------------------------------------ TEMP preview

    /** TEMP preview: run this class to see the screen. Delete before opening the pull request. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Leaderboard preview");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            LeaderboardScreen screen = new LeaderboardScreen();
            frame.setContentPane(screen);
            frame.setSize(1100, 700);
            frame.setMinimumSize(new Dimension(800, 600));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            screen.onShow();
        });
    }
}
