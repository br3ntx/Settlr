package com.settlr.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the Shop sells and every pet the crate can give. Change names and prices here;
 * the codes are stored in the database, so don't rename a code once people own it.
 */
public final class ShopCatalog {

    /** What one pet crate costs. */
    public static final int CRATE_PRICE = 150;
    /** XP given back when the crate gives a pet you already have (half the crate). */
    public static final int DUPLICATE_REFUND = CRATE_PRICE / 2;

    private static final List<Cosmetic> COSMETICS = List.of(
            new Cosmetic("HAT_PARTY", "Party hat", Cosmetic.Slot.HAT, 60),
            new Cosmetic("HAT_BEANIE", "Beanie", Cosmetic.Slot.HAT, 80),
            new Cosmetic("HAT_SALAKOT", "Salakot", Cosmetic.Slot.HAT, 120),
            new Cosmetic("HAT_CROWN", "Crown", Cosmetic.Slot.HAT, 300),
            new Cosmetic("GLASSES_SUN", "Sunglasses", Cosmetic.Slot.GLASSES, 80),
            new Cosmetic("NECK_BOWTIE", "Bow tie", Cosmetic.Slot.NECK, 60),
            new Cosmetic("NECK_SCARF", "Scarf", Cosmetic.Slot.NECK, 100),
            new Cosmetic("HAT_HALO", "Angel halo", Cosmetic.Slot.HAT, 200));

    private static final List<Pet> PETS = List.of(
            new Pet("PET_CAT", "Cat", Pet.Rarity.COMMON),
            new Pet("PET_DOG", "Dog", Pet.Rarity.COMMON),
            new Pet("PET_HAMSTER", "Hamster", Pet.Rarity.COMMON),
            new Pet("PET_BUNNY", "Bunny", Pet.Rarity.RARE),
            new Pet("PET_TURTLE", "Turtle", Pet.Rarity.RARE),
            new Pet("PET_AXOLOTL", "Axolotl", Pet.Rarity.EPIC));

    private ShopCatalog() { }

    public static List<Cosmetic> cosmetics() {
        return COSMETICS;
    }

    public static List<Pet> pets() {
        return PETS;
    }

    /** The pets of one rarity. */
    public static List<Pet> pets(Pet.Rarity rarity) {
        List<Pet> result = new ArrayList<>();
        for (Pet p : PETS) {
            if (p.getRarity() == rarity) {
                result.add(p);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** The item with this code, or null. */
    public static ShopItem find(String code) {
        for (Cosmetic c : COSMETICS) {
            if (c.getCode().equals(code)) {
                return c;
            }
        }
        for (Pet p : PETS) {
            if (p.getCode().equals(code)) {
                return p;
            }
        }
        return null;
    }
}
