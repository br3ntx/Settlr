package com.settlr.ui.pet;

import com.settlr.model.Cosmetic;

import java.awt.geom.Rectangle2D;

// DESIGN PATTERN: Decorator (Concrete Decorator) - adds the glasses.
/** Puts glasses on the pet, with one lens over each eye. */
public class GlassesDecorator extends OutfitDecorator {

    /** In "glasses on pet.png" the lens centers are 50% of the picture's width apart, halfway down. */
    private static final float LENS_GAP = 0.50f;
    private static final float LENS_HEIGHT = 0.50f;
    /** A little bigger than an exact fit, so the frames go around the eyes instead of across them. */
    private static final float ROOMY = 1.12f;

    public GlassesDecorator(PetDrawing wearer, Cosmetic item) {
        super(wearer, item);
    }

    @Override
    protected Rectangle2D.Float place(PetFit fit, float left, float top, float size) {
        float width = fit.eyeGap * size / LENS_GAP * ROOMY;
        float height = heightFor(width);
        return new Rectangle2D.Float(left + fit.eyeX * size - width / 2,
                top + fit.eyeY * size - height * LENS_HEIGHT, width, height);
    }
}
