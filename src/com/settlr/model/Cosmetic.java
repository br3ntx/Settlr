package com.settlr.model;

/** A hat or accessory for your pets, bought in the Shop with XP. */
public class Cosmetic extends ShopItem {

    /** Where it goes on the pet; also the Shop's filters. */
    public enum Slot {
        HAT("Hat"), GLASSES("Glasses"), NECK("Neck");

        private final String label;

        Slot(String label) {
            this.label = label;
        }

        public String getLabel() { return label; }
    }

    private final Slot slot;
    private final int price;

    public Cosmetic(String code, String name, Slot slot, int price) {
        super(code, name);
        if (slot == null) {
            throw new IllegalArgumentException("Slot is required.");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be more than 0 XP.");
        }
        this.slot = slot;
        this.price = price;
    }

    public Slot getSlot() { return slot; }

    /** Price in XP. */
    public int getPrice() { return price; }

    @Override
    public String getTag() { return slot.getLabel().toUpperCase(); }
}
