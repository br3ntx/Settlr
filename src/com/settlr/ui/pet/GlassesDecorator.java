package com.settlr.ui.pet;

import com.settlr.model.Cosmetic;

import java.awt.geom.Point2D;

/** Puts glasses on the pet, on its face. */
public class GlassesDecorator extends OutfitDecorator {

    public GlassesDecorator(PetDrawing wearer, Cosmetic item) {
        super(wearer, item);
    }

    @Override
    protected Point2D.Float where(float cx, float cy, float d) {
        return new Point2D.Float(cx - d * 0.42f, cy - d * 0.2f);
    }
}
