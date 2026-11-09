package com.settlr.ui.pet;

import com.settlr.model.Cosmetic;

import java.awt.geom.Point2D;

/** Puts a hat on the pet, on top of its head. */
public class HatDecorator extends OutfitDecorator {

    public HatDecorator(PetDrawing wearer, Cosmetic item) {
        super(wearer, item);
    }

    @Override
    protected Point2D.Float where(float cx, float cy, float d) {
        return new Point2D.Float(cx, cy - d / 2 - 4);
    }
}
