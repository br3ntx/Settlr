package com.settlr.ui.pet;

import com.settlr.model.Pet;

/**
 * Where a pet's head and eyes are in its picture, so hats and glasses land in the right spot.
 * Every number is a fraction of the square the pet is drawn in, measured from its top-left
 * corner: headTop = 0.02 means the top of the head is 2% down from the top of that square.
 *
 * Each pet picture gets its own numbers (the cat's eyes sit lower than the dog's). A pet with
 * no picture yet uses the numbers for the dashed placeholder circle.
 */
public final class PetFit {

    /** The dashed "Pet picture goes here" circle that PlainPet draws. */
    static final PetFit PLACEHOLDER = new PetFit(0.12f, 0.50f, 0.70f, 0.50f, 0.42f, 0.22f);

    final float headTop;   // top of the head, in the middle
    final float headX;     // middle of the head, left to right
    final float headWidth; // width of the head, without the ears
    final float eyeX;      // halfway between the eyes
    final float eyeY;      // height of the eyes' centers
    final float eyeGap;    // from the center of one eye to the center of the other

    PetFit(float headTop, float headX, float headWidth, float eyeX, float eyeY, float eyeGap) {
        this.headTop = headTop;
        this.headX = headX;
        this.headWidth = headWidth;
        this.eyeX = eyeX;
        this.eyeY = eyeY;
        this.eyeGap = eyeGap;
    }

    /** The numbers for this pet's picture (measured from dog.PNG and cat.PNG). */
    static PetFit of(Pet pet, boolean hasPicture) {
        if (pet == null || !hasPicture) {
            return PLACEHOLDER;
        }
        switch (pet.getCode()) {
            case "PET_DOG": return new PetFit(0.02f, 0.48f, 0.72f, 0.478f, 0.47f, 0.306f);
            case "PET_CAT": return new PetFit(0.06f, 0.44f, 0.72f, 0.437f, 0.50f, 0.294f);
            default:        return new PetFit(0.04f, 0.48f, 0.72f, 0.48f, 0.48f, 0.30f); // a new picture: a good guess
        }
    }
}
