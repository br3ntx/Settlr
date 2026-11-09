package com.settlr.ui.pet;

import com.settlr.model.Cosmetic;

import java.awt.geom.Point2D;

/** Puts a neck item (bow tie, scarf) on the pet, lower down. */
public class NeckDecorator extends OutfitDecorator {

    public NeckDecorator(PetDrawing wearer, Cosmetic item) {
        super(wearer, item);
    }

    @Override
    protected Point2D.Float where(float cx, float cy, float d) {
        return new Point2D.Float(cx + d * 0.42f, cy + d * 0.36f);
    }
}
