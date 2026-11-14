package com.settlr.ui.pet;

import com.settlr.model.Cosmetic;
import com.settlr.model.OwnedPet;
import com.settlr.model.Pet;
import com.settlr.model.ShopCatalog;
import org.junit.Test;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
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
        PetDrawing dressed = new RecordingDecorator(new RecordingDecorator(pet, "glasses", order), "crown", order);

        dressed.draw(canvas(), 50, 50, 100);

        assertEquals(List.of("pet", "glasses", "crown"), order);
    }

    @Test
    public void hatIsTheOutermostLayer() {
        OwnedPet dog = new OwnedPet((Pet) ShopCatalog.find("PET_DOG"));
        dog.wear((Cosmetic) ShopCatalog.find("HAT_CROWN"));
        dog.wear((Cosmetic) ShopCatalog.find("GLASSES_ROUND"));

        PetDrawing drawing = PetDrawings.of(dog);

        assertTrue(drawing instanceof HatDecorator);
        drawing.draw(canvas(), 100, 100, 160); // draws without errors
    }

    @Test
    public void decoratorsUseThePetUnderneathToFindTheHead() {
        OwnedPet cat = new OwnedPet((Pet) ShopCatalog.find("PET_CAT"));
        PetFit plain = PetDrawings.of(cat).fit();
        cat.wear((Cosmetic) ShopCatalog.find("HAT_FEDORA"));
        cat.wear((Cosmetic) ShopCatalog.find("GLASSES_ROUND"));

        assertEquals(plain.headTop, PetDrawings.of(cat).fit().headTop, 0.0001f);
    }

    @Test
    public void everyShopItemIsAHeadItemOrGlasses() {
        for (Cosmetic c : ShopCatalog.cosmetics()) {
            assertTrue(c.getSlot() == Cosmetic.Slot.HAT || c.getSlot() == Cosmetic.Slot.GLASSES);
        }
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
        protected Rectangle2D.Float place(PetFit fit, float left, float top, float size) {
            return new Rectangle2D.Float(left, top, size, size);
        }

        @Override
        protected void drawItem(Graphics2D g2, Rectangle2D.Float box) {
            order.add(name);
        }
    }

    private static Graphics2D canvas() {
        return new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB).createGraphics();
    }
}
