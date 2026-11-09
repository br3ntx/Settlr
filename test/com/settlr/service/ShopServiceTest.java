package com.settlr.service;

import com.settlr.model.Pet;
import com.settlr.model.ShopCatalog;
import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Checks the pet egg's odds. No database needed: only the random pick is tested. */
public class ShopServiceTest {

    @Test
    public void oddsAddUpTo100Percent() {
        int total = 0;
        for (Pet.Rarity r : Pet.Rarity.values()) {
            total += ShopService.chanceOf(r);
        }
        assertEquals(100, total);
    }

    @Test
    public void eggOnlyGivesPetsFromTheCatalog() {
        ShopService shop = new ShopService(new Random(7));
        for (int i = 0; i < 500; i++) {
            assertTrue(ShopCatalog.pets().contains(shop.rollPet()));
        }
    }

    @Test
    public void rarerPetsHatchLessOften() {
        ShopService shop = new ShopService(new Random(123)); // fixed seed: same result every run
        Map<Pet.Rarity, Integer> counts = new EnumMap<>(Pet.Rarity.class);
        int rolls = 20_000;
        for (int i = 0; i < rolls; i++) {
            counts.merge(shop.rollPet().getRarity(), 1, Integer::sum);
        }
        for (Pet.Rarity r : Pet.Rarity.values()) {
            double percent = 100.0 * counts.getOrDefault(r, 0) / rolls;
            assertEquals(r + " should hatch about " + r.getWeight() + "% of the time",
                    r.getWeight(), percent, 1.5);
        }
    }
}
