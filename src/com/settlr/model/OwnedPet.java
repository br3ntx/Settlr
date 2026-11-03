package com.settlr.model;

import java.util.EnumMap;
import java.util.Map;

/**
 * One of the user's pets, with the name they gave it and what it's wearing: at most one
 * cosmetic per slot (a hat, glasses, something on the neck). Each pet keeps its own outfit.
 */
public class OwnedPet {

    public static final int MAX_NAME_LENGTH = 20;

    private final Pet pet;
    private String name; // null = use the pet's kind, e.g. "Bunny"
    private final Map<Cosmetic.Slot, Cosmetic> outfit = new EnumMap<>(Cosmetic.Slot.class);

    public OwnedPet(Pet pet) {
        if (pet == null) {
            throw new IllegalArgumentException("Pet is required.");
        }
        this.pet = pet;
    }

    /** A copy, so a popup can change it and throw the changes away on Cancel. */
    public OwnedPet copy() {
        OwnedPet c = new OwnedPet(pet);
        c.name = name;
        c.outfit.putAll(outfit);
        return c;
    }

    public Pet getPet() { return pet; }

    /** The name the user gave it, or null. */
    public String getName() { return name; }

    /** What to show: the name it was given, or its kind ("Bunny"). */
    public String getDisplayName() {
        return name == null ? pet.getName() : name;
    }

    /** Blank clears the name. Too long names are rejected by PetService. */
    public void setName(String name) {
        this.name = name == null || name.isBlank() ? null : name.trim();
    }

    /** What it wears in this slot, or null. */
    public Cosmetic getItem(Cosmetic.Slot slot) {
        return outfit.get(slot);
    }

    /** Puts an item on (in its own slot). */
    public void wear(Cosmetic item) {
        outfit.put(item.getSlot(), item);
    }

    /** Takes off whatever is in this slot. */
    public void takeOff(Cosmetic.Slot slot) {
        outfit.remove(slot);
    }
}
