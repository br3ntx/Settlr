package com.settlr.ui.pet;

import com.settlr.model.Cosmetic;
import com.settlr.model.OwnedPet;

/** Builds the decorator chain for a pet from what it's wearing. */
public final class PetDrawings {

    private PetDrawings() { }

    /** The pet with its hat, glasses and neck item on; the egg if pet is null. */
    public static PetDrawing of(OwnedPet pet) {
        if (pet == null) {
            return new PlainPet(null);
        }
        PetDrawing drawing = new PlainPet(pet.getPet());
        Cosmetic hat = pet.getItem(Cosmetic.Slot.HAT);
        Cosmetic glasses = pet.getItem(Cosmetic.Slot.GLASSES);
        Cosmetic neck = pet.getItem(Cosmetic.Slot.NECK);
        if (neck != null) {
            drawing = new NeckDecorator(drawing, neck);
        }
        if (glasses != null) {
            drawing = new GlassesDecorator(drawing, glasses);
        }
        if (hat != null) {
            drawing = new HatDecorator(drawing, hat); // drawn last, so it sits on top
        }
        return drawing;
    }
}
