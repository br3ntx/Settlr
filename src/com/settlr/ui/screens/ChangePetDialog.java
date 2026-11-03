package com.settlr.ui.screens;

import com.settlr.model.OwnedPet;
import com.settlr.ui.ModalPopup;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * "Change pet" (opens from Edit pet): your pets as tiles, pick one and press "Use ...".
 * Each pet keeps its own name and outfit. Back, X or Esc goes back to Edit pet unchanged.
 * onDone gets the picked pet's code, or null when nothing changed.
 */
public final class ChangePetDialog extends ModalPopup {

    private static final int FORM_WIDTH = 400;
    private static final int COLUMNS = 3;

    private final Consumer<String> onDone;
    private final List<PickTile> tileList = new ArrayList<>();
    private final RoundedButton use = new RoundedButton("Use");
    private String picked;
    private String result;
    private boolean finished;

    static void open(Component over, List<OwnedPet> pets, String shownCode, Consumer<String> onDone) {
        new ChangePetDialog(pets, shownCode, onDone).showOver(over);
    }

    private ChangePetDialog(List<OwnedPet> pets, String shownCode, Consumer<String> onDone) {
        this.onDone = onDone;
        this.picked = shownCode;

        GlassCard card = new GlassCard(null);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(20, 26, 20, 26));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(ShopScreen.label("Change pet", Font.BOLD, 20, Theme.TEXT), BorderLayout.CENTER);
        JPanel closeHolder = new JPanel(new BorderLayout());
        closeHolder.setOpaque(false);
        closeHolder.add(createCloseButton(), BorderLayout.NORTH);
        header.add(closeHolder, BorderLayout.EAST);
        fixed(header, 32);
        card.add(header);
        JLabel sub = ShopScreen.label("Your pet keeps its name and outfit when you switch back.",
                Font.PLAIN, 12, Theme.MUTED_TEXT);
        card.add(Box.createVerticalStrut(2));
        fixed(sub, 18);
        card.add(sub);

        JPanel grid = new JPanel(new GridLayout(0, COLUMNS, 14, 12));
        grid.setOpaque(false);
        for (OwnedPet p : pets) {
            String code = p.getPet().getCode();
            PickTile t = new PickTile(p.getDisplayName(), PickTile.Look.PET, p.getPet().getRarity().name(),
                    ItemBubble.rarityColor(p.getPet().getRarity()), 112, () -> pick(code, p.getDisplayName()));
            t.putClientProperty("code", code);
            t.putClientProperty("name", p.getDisplayName());
            tileList.add(t);
            grid.add(t);
        }
        for (int i = pets.size(); i % COLUMNS != 0; i++) {
            grid.add(Box.createGlue());
        }
        int rows = (pets.size() + COLUMNS - 1) / COLUMNS;
        card.add(Box.createVerticalStrut(12));
        fixed(grid, rows * 136 + (rows - 1) * 12);
        card.add(grid);

        JLabel more = ShopScreen.label("<html>Get more pets from the pet crate in the "
                + "<b><font color='#E8D9CD'>Shop</font></b>.</html>", Font.PLAIN, 12, Theme.MUTED_TEXT);
        card.add(Box.createVerticalStrut(8));
        fixed(more, 18);
        card.add(more);

        RoundedButton back = RoundedButton.outline("Back");
        for (RoundedButton b : new RoundedButton[] {back, use}) {
            b.setArc(RoundedButton.PILL);
            b.setFont(Theme.font(Font.BOLD, 14));
        }
        back.addActionListener(e -> close());
        use.addActionListener(e -> {
            result = picked;
            close();
        });
        JPanel buttons = new JPanel(new GridBagLayout());
        buttons.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 14);
        buttons.add(back, c);
        c.weightx = 1.5;
        c.insets = new Insets(0, 0, 0, 0);
        buttons.add(use, c);
        card.add(Box.createVerticalStrut(16));
        fixed(buttons, 44);
        card.add(buttons);

        add(card);
        for (OwnedPet p : pets) {
            if (p.getPet().getCode().equals(shownCode)) {
                pick(shownCode, p.getDisplayName());
            }
        }
    }

    private void pick(String code, String name) {
        picked = code;
        for (PickTile t : tileList) {
            t.setPicked(code.equals(t.getClientProperty("code")));
        }
        use.setText("Use " + name);
    }

    /** Every way out (Use, Back, X, Esc) ends here, so Edit pet always comes back. */
    @Override
    public void close() {
        super.close();
        if (!finished) {
            finished = true;
            onDone.accept(result);
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
