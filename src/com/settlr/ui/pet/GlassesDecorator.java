package com.settlr.ui.pet;

import com.settlr.model.Cosmetic;

import java.awt.geom.Rectangle2D;

/** Puts glasses on the pet, with one lens over each eye. */
public class GlassesDecorator extends OutfitDecorator {

    /** In glasses.PNG the lens centers are 51% of the picture's width apart, 73% of the way down. */
    private static final float LENS_GAP = 0.51f;
    private static final float LENS_HEIGHT = 0.73f;
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
