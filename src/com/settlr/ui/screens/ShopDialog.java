package com.settlr.ui.screens;

import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.model.Cosmetic;
import com.settlr.model.Pet;
import com.settlr.model.ShopCatalog;
import com.settlr.service.ShopService;
import com.settlr.ui.ModalPopup;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * The Shop's popups:
 *   - buy(...)   "Buy Crown?" with the price and what you'll have left, or how much more XP you need
 *   - egg(...)   "Hatch a pet egg?", then what came out (a new pet, or one you had and some XP back)
 * onChanged runs after anything was bought, so the Shop page can reload.
 */
public final class ShopDialog extends ModalPopup {

    private static final int FORM_WIDTH = 360;

    private final ShopService shopService = new ShopService();
    private final GlassCard card = new GlassCard(null);
    private final Runnable onChanged;
    private int balance;

    /** Opens "Buy X?" for a hat or accessory. */
    public static void buy(Component over, Cosmetic item, int balance, Runnable onChanged) {
        ShopDialog d = new ShopDialog(balance, onChanged);
        d.showBuy(item);
        d.showOver(over);
    }

    /** Opens "Hatch a pet egg?". */
    public static void egg(Component over, int balance, Runnable onChanged) {
        ShopDialog d = new ShopDialog(balance, onChanged);
        d.showEggConfirm();
        d.showOver(over);
    }

    private ShopDialog(int balance, Runnable onChanged) {
        this.balance = balance;
        this.onChanged = onChanged;
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 26, 22, 26));
        add(card);
    }

    // =====================================================================
    //  The three states
    // =====================================================================

    private void showBuy(Cosmetic item) {
        int price = item.getPrice();
        boolean enough = balance >= price;
        start(null);
        bubble(ItemBubble.of(item, 30), 168, 140);
        title("Buy " + item.getName() + "?");
        line(ShopScreen.xp(price) + ShopScreen.DOT + (enough
                ? "you'll have " + ShopScreen.xp(balance - price) + " left"
                : "you have " + ShopScreen.xp(balance)), Theme.MUTED_TEXT);
        JLabel error = errorLine();

        RoundedButton main = new RoundedButton(enough ? "Buy for " + ShopScreen.xp(price)
                : "Need " + (price - balance) + " more XP");
        main.setEnabled(enough);
        main.addActionListener(e -> {
            main.setEnabled(false); // stops a double click from buying twice
            try {
                shopService.buy(item.getCode());
            } catch (SettlrException ex) {
                showError(error, ex);
                main.setEnabled(true);
                return;
            }
            close();
            onChanged.run();
        });
        buttons(enough ? "Cancel" : "Close", this::close, main);
        finish();
    }

    private void showEggConfirm() {
        int price = ShopCatalog.EGG_PRICE;
        boolean enough = balance >= price;
        start(null);
        bubble(ItemBubble.mystery(30), 150, 130);
        title("Hatch a pet egg?");
        line(ShopScreen.xp(price) + ShopScreen.DOT + (enough
                ? "you'll have " + ShopScreen.xp(balance - price) + " left"
                : "you have " + ShopScreen.xp(balance)), Theme.MUTED_TEXT);
        JLabel error = errorLine();

        RoundedButton main = new RoundedButton(enough ? "Hatch for " + ShopScreen.xp(price)
                : "Need " + (price - balance) + " more XP");
        main.setEnabled(enough);
        main.addActionListener(e -> open(main, error));
        buttons(enough ? "Cancel" : "Close", this::close, main);
        finish();
    }

    private void open(RoundedButton main, JLabel error) {
        main.setEnabled(false);
        ShopService.HatchResult result;
        try {
            result = shopService.hatchEgg();
        } catch (SettlrException ex) {
            showError(error, ex);
            main.setEnabled(true);
            return;
        }
        balance = result.getBalanceAfter();
        onChanged.run();
        showEggResult(result);
    }

    private void showEggResult(ShopService.HatchResult result) {
        Pet pet = result.getPet();
        start("PET EGG");
        ItemBubble bubble = ItemBubble.of(pet, 30);
        Color rarity = ItemBubble.rarityColor(pet.getRarity());
        if (result.isDuplicate()) {
            bubble.outline(new Color(255, 255, 255, 70), false);
        } else {
            bubble.cornerTag("NEW", ItemBubble.XP_GOLD)
                    .outline(pet.getRarity() == Pet.Rarity.COMMON ? new Color(232, 217, 205, 150) : rarity, true);
        }
        bubble(bubble, 186, 150);
        if (result.isDuplicate()) {
            title("You already have " + article(pet.getName()) + " " + pet.getName());
            line("+" + ShopScreen.xp(ShopCatalog.DUPLICATE_REFUND) + " back, so this egg cost "
                    + ShopScreen.xp(ShopCatalog.EGG_PRICE - ShopCatalog.DUPLICATE_REFUND) + ".", Theme.MINT);
        } else {
            title("You got " + article(pet.getName()) + " " + pet.getName() + "!");
            line("It can wear any hat or accessory you own.", Theme.MUTED_TEXT);
        }
        JLabel error = errorLine();

        boolean enough = balance >= ShopCatalog.EGG_PRICE;
        RoundedButton again = new RoundedButton(enough
                ? "Hatch another" + ShopScreen.DOT + ShopScreen.xp(ShopCatalog.EGG_PRICE)
                : "Need " + (ShopCatalog.EGG_PRICE - balance) + " more XP");
        again.setEnabled(enough);
        again.addActionListener(e -> open(again, error));
        buttons("Done", this::close, again);
        finish();
    }

    // =====================================================================
    //  Building blocks
    // =====================================================================

    /** Clears the card and adds the top line: an optional gold heading and the close X. */
    private void start(String heading) {
        card.removeAll();
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        if (heading != null) {
            JLabel h = ShopScreen.label(heading, Font.BOLD, 13, Theme.AMBER);
            h.setHorizontalAlignment(SwingConstants.CENTER);
            h.setBorder(new EmptyBorder(0, 32, 0, 0)); // balances the X so the heading is centered
            top.add(h, BorderLayout.CENTER);
        }
        top.add(createCloseButton(), BorderLayout.EAST);
        fixed(top, 32);
        card.add(top);
    }

    private void bubble(ItemBubble bubble, int width, int height) {
        bubble.setPreferredSize(new Dimension(width, height));
        JPanel holder = ShopScreen.centered(bubble);
        fixed(holder, height);
        card.add(holder);
    }

    private void title(String text) {
        JLabel l = ShopScreen.label(text, Font.BOLD, 22, Theme.TEXT);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(Box.createVerticalStrut(18));
        fixed(l, 30);
        card.add(l);
    }

    private void line(String text, Color color) {
        JLabel l = ShopScreen.label(text, Font.PLAIN, 13, color);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(Box.createVerticalStrut(6));
        fixed(l, 18);
        card.add(l);
    }

    private JLabel errorLine() {
        JLabel l = ShopScreen.label(" ", Font.PLAIN, 12, Theme.DANGER);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(Box.createVerticalStrut(6));
        fixed(l, 16);
        card.add(l);
        return l;
    }

    private void buttons(String secondaryText, Runnable secondary, RoundedButton main) {
        RoundedButton other = RoundedButton.outline(secondaryText);
        other.setArc(RoundedButton.PILL);
        other.setFont(Theme.font(Font.BOLD, 14));
        other.addActionListener(e -> secondary.run());
        main.setArc(RoundedButton.PILL);
        main.setFont(Theme.font(Font.BOLD, 14));
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 14);
        row.add(other, c);
        c.weightx = 1.6;
        c.insets = new Insets(0, 0, 0, 0);
        row.add(main, c);
        card.add(Box.createVerticalStrut(8));
        fixed(row, 44);
        card.add(row);
    }

    private void finish() {
        card.revalidate();
        card.repaint();
        revalidate();
        repaint();
    }

    private static void showError(JLabel error, SettlrException ex) {
        if (ex instanceof DataAccessException) {
            error.setText("Can't save right now. Is MySQL running?");
            ex.printStackTrace();
        } else {
            error.setText(ex.getMessage());
        }
    }

    private static String article(String name) {
        return "AEIOU".indexOf(Character.toUpperCase(name.charAt(0))) >= 0 ? "an" : "a";
    }

    /** Every row is the same width, so the centered text lines up. */
    private static void fixed(JComponent c, int height) {
        Dimension d = new Dimension(FORM_WIDTH, height);
        c.setPreferredSize(d);
        c.setMinimumSize(d);
        c.setMaximumSize(d);
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
}
