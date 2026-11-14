package com.settlr.ui.pet;

import com.settlr.model.Cosmetic;

import java.awt.geom.Rectangle2D;

/**
 * Puts something on the pet's head: a hat on top in the middle, or the bow off to one side.
 *
 * Each item has three numbers, all compared with the head:
 *   width  how wide the item is, as a share of the head's width
 *   shift  how far right of the middle it sits, as a share of the head's width (the bow)
 *   sink   how much of the item's height goes down over the head (a fedora sits low, a party hat high)
 */
public class HatDecorator extends OutfitDecorator {

    public HatDecorator(PetDrawing wearer, Cosmetic item) {
        super(wearer, item);
    }

    @Override
    protected Rectangle2D.Float place(PetFit fit, float left, float top, float size) {
        float[] n = numbersFor(item.getCode());
        float width = fit.headWidth * size * n[0];
        float height = heightFor(width);
        float x = left + (fit.headX + fit.headWidth * n[1]) * size - width / 2;
        float y = top + fit.headTop * size - height * (1 - n[2]);
        return new Rectangle2D.Float(x, y, width, height);
    }

    /** {width, shift, sink} for each item; anything new gets a middle-sized hat. */
    private static float[] numbersFor(String code) {
        switch (code) {
            case "HAT_PARTY":  return new float[] {0.32f, 0f, 0.10f};
            case "HAT_FEDORA": return new float[] {0.74f, 0f, 0.40f};
            case "HAT_CROWN":  return new float[] {0.42f, 0f, 0.22f};
            case "HAT_BOW":    return new float[] {0.32f, 0.26f, 0.70f};
            default:           return new float[] {0.45f, 0f, 0.25f};
        }
    }
}
