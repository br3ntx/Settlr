package com.settlr.ui.screens;

import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.model.Cosmetic;
import com.settlr.model.Household;
import com.settlr.model.Pet;
import com.settlr.model.ShopCatalog;
import com.settlr.model.ShopItem;
import com.settlr.service.ShopService;
import com.settlr.ui.BaseScreen;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.SegmentedControl;
import com.settlr.ui.components.SlimScrollBarUI;
import com.settlr.ui.theme.Theme;
import com.settlr.util.Session;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shop page (sidebar: Shop): your XP, the pet egg, and the hats and accessories you can buy.
 * Click an item to buy it; the pet egg gives a random pet. Pets you own show under "Owned".
 * The rules (prices, balance checks, the odds) live in ShopService.
 */
public class ShopScreen extends BaseScreen {

    static final String DOT = " \u00B7 ";
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.ENGLISH);
    private static final String[] FILTERS = {"All", "Hats", "Glasses", "Neck", "Owned"};

    private final ShopService shopService = new ShopService();

    private final JLabel crumb = label(" ", Font.PLAIN, 12, Theme.MUTED_TEXT);
    private final XpPill xpPill = new XpPill();
    private final JLabel messageLabel = new JLabel(" ");
    private final RoundedButton hatchButton = new RoundedButton("Hatch for " + ShopCatalog.EGG_PRICE + " XP");
    private final SegmentedControl filter = new SegmentedControl(FILTERS, i -> fillGrid());
    private final JPanel grid = new JPanel(new GridLayout(0, 4, 26, 22));

    private int balance;
    private Set<String> owned = Collections.emptySet();

    public ShopScreen() {
        setBackground(Theme.BACKGROUND);
        setLayout(new BorderLayout());

        JPanel page = vertical();
        page.setBorder(new EmptyBorder(0, 40, 28, 40)); // the top bar above already leaves room

        JPanel titles = vertical();
        titles.add(left(crumb));
        titles.add(Box.createVerticalStrut(2));
        titles.add(left(label("Shop", Font.BOLD, 30, Theme.PRIMARY)));
        JPanel xpSide = vertical();
        JPanel pillRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pillRow.setOpaque(false);
        pillRow.add(xpPill);
        xpSide.add(right(pillRow));
        xpSide.add(Box.createVerticalStrut(6));
        xpSide.add(right(label("Earn XP by finishing tasks and paying on time", Font.PLAIN, 12, Theme.MUTED_TEXT)));
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(titles, BorderLayout.WEST);
        JPanel xpHolder = new JPanel(new BorderLayout());
        xpHolder.setOpaque(false);
        xpHolder.add(xpSide, BorderLayout.SOUTH);
        header.add(xpHolder, BorderLayout.EAST);
        page.add(left(fitHeight(header)));

        messageLabel.setFont(Theme.font(Font.PLAIN, 12));
        messageLabel.setBorder(new EmptyBorder(4, 2, 4, 0));
        page.add(left(messageLabel));
        page.add(left(buildEggBanner()));
        page.add(Box.createVerticalStrut(22));
        page.add(left(filter));
        page.add(Box.createVerticalStrut(16));
        grid.setOpaque(false);
        page.add(left(grid));

        JPanel holder = new JPanel(new BorderLayout()); // keeps everything at the top
        holder.setOpaque(false);
        holder.add(page, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        SlimScrollBarUI.apply(scroll);
        add(scroll, BorderLayout.CENTER);
    }

    @Override
    public void onShow() {
        Household h = Session.getInstance().getCurrentHousehold();
        crumb.setText((h == null ? "" : h.getName() + DOT) + "Shop");
        filter.setSelected(0);
        reload();
    }

    /** Loads the XP and owned items again and redraws the grid. */
    private void reload() {
        try {
            balance = shopService.getBalance();
            owned = shopService.getOwnedCodes();
            showMessage(" ", false);
        } catch (DataAccessException e) {
            balance = 0;
            owned = Collections.emptySet();
            showMessage("Can't load the Shop. If MySQL is running, run database/migrations/003_add_shop.sql once.",
                    true);
            e.printStackTrace();
        } catch (SettlrException e) {
            showMessage(e.getMessage(), true);
        }
        xpPill.setXp(balance);
        fillGrid();
    }

    private void fillGrid() {
        grid.removeAll();
        List<ShopItem> items = new ArrayList<>();
        int f = filter.getSelected();
        for (Cosmetic c : ShopCatalog.cosmetics()) {
            boolean show = f == 0
                    || (f == 1 && c.getSlot() == Cosmetic.Slot.HAT)
                    || (f == 2 && c.getSlot() == Cosmetic.Slot.GLASSES)
                    || (f == 3 && c.getSlot() == Cosmetic.Slot.NECK)
                    || (f == 4 && owned.contains(c.getCode()));
            if (show) {
                items.add(c);
            }
        }
        if (f == 4) { // pets only show under Owned; the rest of the time they're a surprise
            for (Pet p : ShopCatalog.pets()) {
                if (owned.contains(p.getCode())) {
                    items.add(p);
                }
            }
        }
        if (items.isEmpty()) {
            grid.setLayout(new BorderLayout());
            JLabel empty = label("Nothing here yet. Buy something or hatch a pet egg.", Font.PLAIN, 13,
                    Theme.MUTED_TEXT);
            empty.setBorder(new EmptyBorder(30, 2, 0, 0));
            grid.add(empty, BorderLayout.NORTH);
        } else {
            grid.setLayout(new GridLayout(0, 4, 26, 22));
            for (ShopItem item : items) {
                grid.add(new ItemTile(item));
            }
            for (int i = items.size(); i % 4 != 0; i++) { // keeps a short last row the same width
                grid.add(Box.createGlue());
            }
        }
        grid.revalidate();
        grid.repaint();
    }

    private void buy(Cosmetic item) {
        ShopDialog.buy(this, item, balance, () -> {
            reload();
            showMessage("You bought the " + item.getName() + ".", false);
        });
    }

    private void hatchEgg() {
        ShopDialog.egg(this, balance, this::reload);
    }

    private void showMessage(String text, boolean error) {
        messageLabel.setForeground(error ? Theme.DANGER : Theme.MINT);
        messageLabel.setText(text);
    }

    // =====================================================================
    //  Pieces
    // =====================================================================

    private JComponent buildEggBanner() {
        JPanel banner = new JPanel(new BorderLayout(26, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Shape box = new RoundRectangle2D.Float(0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f, 34, 34);
                g2.setPaint(new GradientPaint(0, 0, new Color(0x3A4540), getWidth(), 0, new Color(0x2C3838)));
                g2.fill(box);
                g2.setColor(new Color(201, 164, 110, 105));
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(box);
                g2.dispose();
            }
        };
        banner.setOpaque(false);
        banner.setBorder(new EmptyBorder(22, 26, 22, 28));

        ItemBubble box = ItemBubble.mystery(24);
        box.setPreferredSize(new Dimension(108, 108));
        banner.add(box, BorderLayout.WEST);

        StringBuilder odds = new StringBuilder();
        for (Pet.Rarity r : Pet.Rarity.values()) {
            odds.append(r.getLabel()).append(' ').append(ShopService.chanceOf(r)).append('%').append(DOT);
        }
        odds.append("a pet you already have gives ").append(ShopCatalog.DUPLICATE_REFUND).append(" XP back");
        JPanel text = vertical();
        text.add(left(label("Pet egg", Font.BOLD, 20, Theme.TEXT)));
        text.add(Box.createVerticalStrut(6));
        text.add(left(label("Get a random pet to wear your hats and accessories.", Font.PLAIN, 13,
                new Color(0xB5BFBC))));
        text.add(Box.createVerticalStrut(6));
        text.add(left(label(odds.toString(), Font.PLAIN, 12, Theme.MUTED_TEXT)));
        JPanel textHolder = new JPanel(new GridBagLayout());
        textHolder.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        textHolder.add(text, c);
        banner.add(textHolder, BorderLayout.CENTER);

        hatchButton.setArc(RoundedButton.PILL);
        hatchButton.setFont(Theme.font(Font.BOLD, 14));
        hatchButton.setPreferredSize(new Dimension(
                hatchButton.getFontMetrics(hatchButton.getFont()).stringWidth(hatchButton.getText()) + 52, 44));
        hatchButton.addActionListener(e -> hatchEgg());
        banner.add(centered(hatchButton), BorderLayout.EAST);
        return fitHeight(banner);
    }

    /** One item in the grid: its bubble, name, and price (or "Owned"). */
    private final class ItemTile extends JPanel {

        ItemTile(ShopItem item) {
            super(new BorderLayout(0, 10));
            setOpaque(false);
            boolean have = owned.contains(item.getCode());
            ItemBubble bubble = ItemBubble.of(item, 30);
            bubble.setPreferredSize(new Dimension(150, 168));
            if (have) {
                bubble.cornerTag("Owned", Theme.MINT).outline(new Color(95, 208, 176, 120), false);
            }
            add(bubble, BorderLayout.CENTER);

            JPanel text = vertical();
            JLabel name = label(item.getName(), Font.BOLD, 15, Theme.TEXT);
            name.setAlignmentX(Component.CENTER_ALIGNMENT);
            text.add(name);
            text.add(Box.createVerticalStrut(3));
            String bottom;
            Color bottomColor;
            if (item instanceof Pet) {
                bottom = ((Pet) item).getRarity().getLabel() + " pet";
                bottomColor = ItemBubble.rarityColor(((Pet) item).getRarity());
            } else if (have) {
                bottom = "Owned";
                bottomColor = Theme.MINT;
            } else {
                bottom = NUMBER.format(((Cosmetic) item).getPrice()) + " XP";
                bottomColor = ItemBubble.XP_GOLD;
            }
            JLabel price = label(bottom, Font.BOLD, 14, bottomColor);
            price.setAlignmentX(Component.CENTER_ALIGNMENT);
            text.add(price);
            add(text, BorderLayout.SOUTH);

            if (!have && item instanceof Cosmetic) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("Buy the " + item.getName());
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) { buy((Cosmetic) item); }
                    @Override public void mouseEntered(MouseEvent e) { bubble.setHover(true); }
                    @Override public void mouseExited(MouseEvent e) { bubble.setHover(false); }
                });
            }
        }
    }

    /** The gold XP balance in the top-right corner. */
    static final class XpPill extends JComponent {
        private String text = "0";

        XpPill() {
            setFont(Theme.font(Font.BOLD, 18));
            setXp(0);
        }

        void setXp(int xp) {
            text = NUMBER.format(xp);
            FontMetrics fm = getFontMetrics(getFont());
            Dimension d = new Dimension(fm.stringWidth(text) + 70, 46);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Shape pill = new RoundRectangle2D.Float(0.75f, 0.75f, w - 1.5f, h - 1.5f, h, h);
            g2.setColor(new Color(201, 164, 110, 30));
            g2.fill(pill);
            g2.setColor(new Color(201, 164, 110, 120));
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(pill);
            paintCoin(g2, 18, (h - 22) / 2, 22);
            g2.setFont(getFont());
            g2.setColor(ItemBubble.XP_GOLD);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, 50, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }

        /** A small gold coin with "XP" on it. */
        static void paintCoin(Graphics2D g2, int x, int y, int d) {
            g2.setColor(Theme.AMBER);
            g2.fill(new Ellipse2D.Float(x, y, d, d));
            g2.setColor(Theme.ON_PRIMARY);
            g2.setFont(Theme.font(Font.BOLD, Math.round(d * 0.42f)));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("XP", x + (d - fm.stringWidth("XP")) / 2f, y + (d - fm.getHeight()) / 2f + fm.getAscent());
        }
    }

    // =====================================================================
    //  Small helpers
    // =====================================================================

    static JLabel label(String text, int style, int size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(style, size));
        l.setForeground(color);
        return l;
    }

    static JPanel vertical() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    static JPanel centered(JComponent c) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        p.add(c);
        return p;
    }

    private static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static <T extends JComponent> T right(T c) {
        c.setAlignmentX(Component.RIGHT_ALIGNMENT);
        return c;
    }

    private static <T extends JComponent> T fitHeight(T c) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    static String xp(int amount) {
        return NUMBER.format(amount) + " XP";
    }
}
