package com.settlr.ui.pet;

import java.awt.Graphics2D;

/**
 * Something that draws a pet (the "component" of the Decorator pattern).
 *
 * A plain pet is a PlainPet. Each item it wears wraps it in a decorator that draws the pet first,
 * then its item on top:
 *
 *     PetDrawing look = new NeckDecorator(new HatDecorator(new PlainPet(dog), crown), scarf);
 *     look.draw(g2, cx, cy, size);   // dog, then crown, then scarf
 *
 * PetDrawings.of(ownedPet) builds that chain from what the pet is wearing.
 */
public interface PetDrawing {

    /** Draws the pet in a square of this size, centered on (cx, cy). */
    void draw(Graphics2D g2, float cx, float cy, float size);
}
