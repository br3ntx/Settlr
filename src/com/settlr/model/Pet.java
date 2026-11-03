package com.settlr.model;

/** A pet from the pet crate. Rarer pets come out of the crate less often. */
public class Pet extends ShopItem {

    /** How likely a pet is to come out of the crate (weights add up to 100). */
    public enum Rarity {
        COMMON(70), RARE(25), EPIC(5);

        private final int weight;

        Rarity(int weight) {
            this.weight = weight;
        }

        /** Chance out of 100 that the crate gives a pet of this rarity. */
        public int getWeight() { return weight; }

        public String getLabel() {
            return name().charAt(0) + name().substring(1).toLowerCase();
        }
    }

    private final Rarity rarity;

    public Pet(String code, String name, Rarity rarity) {
        super(code, name);
        if (rarity == null) {
            throw new IllegalArgumentException("Rarity is required.");
        }
        this.rarity = rarity;
    }

    public Rarity getRarity() { return rarity; }

    @Override
    public String getTag() { return rarity.name(); }
}
