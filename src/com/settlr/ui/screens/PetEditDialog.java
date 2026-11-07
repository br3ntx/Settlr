package com.settlr.ui.screens;

import com.settlr.exception.DataAccessException;
import com.settlr.exception.SettlrException;
import com.settlr.model.Cosmetic;
import com.settlr.model.OwnedPet;
import com.settlr.model.ShopCatalog;
import com.settlr.service.PetService;
import com.settlr.ui.ModalPopup;
import com.settlr.ui.Navigator;
import com.settlr.ui.components.GlassCard;
import com.settlr.ui.components.RoundedButton;
import com.settlr.ui.components.SegmentedControl;
import com.settlr.ui.components.StyledTextField;
import com.settlr.ui.theme.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Edit pet" (from Home): the pet in the middle, its name (Rename), "Change pet" to show a
 * different pet, and Hat / Glasses / Neck tabs to pick what it wears from the items you own.
 * The preview changes as you click; nothing is saved until Save.
 *
 *     PetEditDialog.open(this, pets, shownCode, ownedCodes, () -> reload());
 */
public final class PetEditDialog extends ModalPopup {

    private static final int FORM_WIDTH = 470;
    private static final int COLUMNS = 5;

    private final PetService petService = new PetService();
    private final Map<String, OwnedPet> pets = new LinkedHashMap<>(); // copies; saved on Save
    private final Set<String> owned;
    private final Runnable onSaved;
    private String shownCode;
    private Cosmetic.Slot slot = Cosmetic.Slot.HAT;
    private boolean renaming;

    private final GlassCard card = new GlassCard(null);
    private final PetFigure figure = new PetFigure(220, 196);
    private final JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
    private final StyledTextField nameField = new StyledTextField("Give it a name");
    private final JLabel kindLabel = ShopScreen.label(" ", Font.PLAIN, 12, Theme.MUTED_TEXT);
    private final JPanel tiles = new JPanel(new GridLayout(0, COLUMNS, 12, 10));
    private final JLabel emptyLabel = ShopScreen.label(" ", Font.PLAIN, 12, Theme.MUTED_TEXT);
    private final JLabel errorLabel = ShopScreen.label(" ", Font.PLAIN, 12, Theme.DANGER);
    private final RoundedButton changePet = TasksScreen.smallButton("Change pet");

    public static void open(Component over, List<OwnedPet> myPets, String shownCode, Set<String> owned,
                            Runnable onSaved) {
        new PetEditDialog(myPets, shownCode, owned, onSaved).showOver(over);
    }

    private PetEditDialog(List<OwnedPet> myPets, String shownCode, Set<String> owned, Runnable onSaved) {
        for (OwnedPet p : myPets) {
            pets.put(p.getPet().getCode(), p.copy());
        }
        this.shownCode = shownCode;
        this.owned = owned;
        this.onSaved = onSaved;

        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(20, 26, 20, 26));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(ShopScreen.label("Edit pet", Font.BOLD, 20, Theme.TEXT), BorderLayout.CENTER);
        JPanel closeHolder = new JPanel(new BorderLayout());
        closeHolder.setOpaque(false);
        closeHolder.add(createCloseButton(), BorderLayout.NORTH);
        header.add(closeHolder, BorderLayout.EAST);
        row(header, 0, 32);

        row(ShopScreen.centered(figure), 0, 196);

        nameRow.setOpaque(false);
        row(nameRow, 4, 34);
        nameField.setFont(Theme.font(Font.PLAIN, 13));
        StyledTextField.usePillStyle(nameField);
        nameField.setPreferredSize(new Dimension(200, 34));
        nameField.addActionListener(e -> finishRename());

        kindLabel.setHorizontalAlignment(SwingConstants.CENTER);
        row(kindLabel, 2, 18);

        changePet.addActionListener(e -> openChangePet());
        JPanel changeHolder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        changeHolder.setOpaque(false);
        changeHolder.add(changePet);
        row(changeHolder, 8, 28);
        changeHolder.setVisible(pets.size() > 1); // nothing to change to with one pet

        SegmentedControl tabs = new SegmentedControl(new String[] {"Hat", "Glasses", "Neck"}, i -> {
            slot = Cosmetic.Slot.values()[i];
            fillTiles();
        });
        JPanel tabsHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabsHolder.setOpaque(false);
        tabsHolder.add(tabs);
        row(tabsHolder, 16, 32);

        tiles.setOpaque(false);
        tiles.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(Box.createVerticalStrut(10));
        card.add(tiles);
        row(emptyLabel, 4, 16);

        JLabel shopHint = ShopScreen.label("<html>Want more? Hats and accessories are in the "
                + "<b><font color='#E8D9CD'>Shop</font></b>.</html>", Font.PLAIN, 12, Theme.MUTED_TEXT);
        shopHint.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        shopHint.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                close();
                Navigator.getInstance().show(Navigator.SHOP);
            }
        });
        row(shopHint, 2, 18);
        row(errorLabel, 6, 16);

        RoundedButton cancel = RoundedButton.outline("Cancel");
        RoundedButton save = new RoundedButton("Save");
        for (RoundedButton b : new RoundedButton[] {cancel, save}) {
            b.setArc(RoundedButton.PILL);
            b.setFont(Theme.font(Font.BOLD, 14));
        }
        cancel.addActionListener(e -> close());
        save.addActionListener(e -> save(save));
        JPanel buttons = new JPanel(new GridBagLayout());
        buttons.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 14);
        buttons.add(cancel, c);
        c.weightx = 1.5;
        c.insets = new Insets(0, 0, 0, 0);
        buttons.add(save, c);
        row(buttons, 6, 44);

        add(card);
        refreshPet();
    }

    // =====================================================================
    //  Showing the pet
    // =====================================================================

    private OwnedPet current() {
        return pets.get(shownCode);
    }

    /** Redraws everything about the shown pet: preview, name, kind, and the tiles. */
    private void refreshPet() {
        OwnedPet p = current();
        figure.setPet(p);
        fillNameRow();
        kindLabel.setText(kindHtml(p));
        fillTiles();
    }

    private void fillNameRow() {
        nameRow.removeAll();
        if (renaming) {
            nameField.setText(current().getName() == null ? "" : current().getName());
            nameRow.add(nameField);
            RoundedButton done = TasksScreen.smallButton("Done");
            done.addActionListener(e -> finishRename());
            nameRow.add(done);
            SwingUtilities.invokeLater(nameField::requestFocusInWindow);
        } else {
            nameRow.add(ShopScreen.label(current().getDisplayName(), Font.BOLD, 21, Theme.TEXT));
            RoundedButton rename = TasksScreen.smallButton("Rename");
            rename.addActionListener(e -> {
                renaming = true;
                fillNameRow();
            });
            nameRow.add(rename);
        }
        nameRow.revalidate();
        nameRow.repaint();
    }

    private void finishRename() {
        String name = nameField.getText().trim();
        if (name.length() > OwnedPet.MAX_NAME_LENGTH) {
            errorLabel.setText("Pet names can be up to " + OwnedPet.MAX_NAME_LENGTH + " characters.");
            return;
        }
        errorLabel.setText(" ");
        current().setName(name);
        renaming = false;
        fillNameRow();
        figure.repaint();
    }

    /** "None" and every item you own for the chosen slot. */
    private void fillTiles() {
        tiles.removeAll();
        OwnedPet p = current();
        List<PickTile> all = new ArrayList<>();
        PickTile none = new PickTile("None", PickTile.Look.NONE, null, null, 74, () -> {
            p.takeOff(slot);
            afterPick();
        });
        none.setPicked(p.getItem(slot) == null);
        all.add(none);
        for (Cosmetic item : ShopCatalog.cosmetics()) {
            if (item.getSlot() != slot || !owned.contains(item.getCode())) {
                continue;
            }
            PickTile t = new PickTile(item.getName(), PickTile.Look.ITEM, null, null, 74, () -> {
                p.wear(item);
                afterPick();
            });
            t.setPicked(p.getItem(slot) == item);
            all.add(t);
        }
        for (PickTile t : all) {
            tiles.add(t);
        }
        for (int i = all.size(); i % COLUMNS != 0; i++) { // keeps the tiles the same width
            tiles.add(Box.createGlue());
        }
        int rows = (all.size() + COLUMNS - 1) / COLUMNS;
        Dimension d = new Dimension(FORM_WIDTH, rows * 98 + (rows - 1) * 10);
        tiles.setPreferredSize(d);
        tiles.setMinimumSize(d);
        tiles.setMaximumSize(d);
        emptyLabel.setText(all.size() == 1
                ? "You don't have any " + (slot == Cosmetic.Slot.HAT ? "hats" : slot == Cosmetic.Slot.GLASSES
                        ? "glasses" : "neck items") + " yet."
                : " ");
        card.revalidate();
        card.repaint();
    }

    private void afterPick() {
        figure.repaint();
        fillTiles();
    }

    // =====================================================================
    //  Change pet and Save
    // =====================================================================

    /** Hides this popup while Change pet is open, then comes back with the pet that was picked. */
    private void openChangePet() {
        if (renaming) {
            finishRename();
        }
        Component over = getParent();
        close();
        ChangePetDialog.open(over, new ArrayList<>(pets.values()), shownCode, picked -> {
            if (picked != null) {
                shownCode = picked;
                refreshPet();
            }
            showOver(over);
        });
    }

    private void save(RoundedButton save) {
        if (renaming) {
            finishRename();
            if (renaming) {
                return; // the name is too long
            }
        }
        errorLabel.setText(" ");
        save.setEnabled(false);
        try {
            petService.save(shownCode, pets.values());
        } catch (DataAccessException e) {
            errorLabel.setText("Can't save right now. Is MySQL running?");
            e.printStackTrace();
            save.setEnabled(true);
            return;
        } catch (SettlrException e) {
            errorLabel.setText(e.getMessage());
            save.setEnabled(true);
            return;
        }
        close();
        onSaved.run();
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private void row(JComponent comp, int spaceAbove, int height) {
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

    /** "Bunny \u00B7 Rare" for a named pet; just "Rare pet" when it's still called "Bunny". */
    static String kindHtml(OwnedPet p) {
        String rarity = "<b><font color='" + hex(ItemBubble.rarityColor(p.getPet().getRarity())) + "'>"
                + p.getPet().getRarity().getLabel() + "</font></b>";
        return "<html>" + (p.getName() == null ? rarity + " pet" : p.getPet().getName() + ShopScreen.DOT + rarity)
                + "</html>";
    }

    static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}
