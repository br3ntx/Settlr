package com.settlr.ui.pet;

import com.settlr.model.Cosmetic;
import com.settlr.model.OwnedPet;
import com.settlr.model.Pet;
import com.settlr.model.ShopCatalog;
import org.junit.Test;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The outfit decorators draw the pet first, then each item on top, in wrapping order. */
public class PetDrawingsTest {

    @Test
    public void eachDecoratorDrawsWhatItWrapsFirst() {
        List<String> order = new ArrayList<>();
        PetDrawing pet = (g2, cx, cy, size) -> order.add("pet");
        PetDrawing dressed = new RecordingDecorator(new RecordingDecorator(pet, "scarf", order), "crown", order);

        dressed.draw(canvas(), 50, 50, 100);

        assertEquals(List.of("pet", "scarf", "crown"), order);
    }

    @Test
    public void hatIsTheOutermostLayer() {
        OwnedPet dog = new OwnedPet((Pet) ShopCatalog.find("PET_DOG"));
        dog.wear((Cosmetic) ShopCatalog.find("HAT_CROWN"));
        dog.wear((Cosmetic) ShopCatalog.find("NECK_SCARF"));

        PetDrawing drawing = PetDrawings.of(dog);

        assertTrue(drawing instanceof HatDecorator);
        drawing.draw(canvas(), 100, 100, 160); // draws without errors
    }

    @Test
    public void noOutfitIsJustThePlainPet() {
        assertTrue(PetDrawings.of(new OwnedPet((Pet) ShopCatalog.find("PET_CAT"))) instanceof PlainPet);
        assertTrue(PetDrawings.of(null) instanceof PlainPet); // the egg
    }

    /** A decorator that writes down when it draws, instead of drawing. */
    private static final class RecordingDecorator extends OutfitDecorator {
        private final String name;
        private final List<String> order;

        RecordingDecorator(PetDrawing wearer, String name, List<String> order) {
            super(wearer, (Cosmetic) ShopCatalog.find("HAT_PARTY"));
            this.name = name;
            this.order = order;
        }

        @Override
        protected Point2D.Float where(float cx, float cy, float d) {
            return new Point2D.Float(cx, cy);
        }

        @Override
        protected void drawItem(Graphics2D g2, float x, float y) {
            order.add(name);
        }
    }

    private static Graphics2D canvas() {
        return new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB).createGraphics();
    }
}
