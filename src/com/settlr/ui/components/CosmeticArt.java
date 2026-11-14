package com.settlr.ui.components;

import com.settlr.model.Cosmetic;
import com.settlr.model.ShopCatalog;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Pictures of the hats and accessories, from resources/images. The file is the item's name in
 * lower case with dashes for spaces: "Party hat" is party-hat.png, "Crown" is crown.png.
 *
 * These pictures already have a see-through background, so unlike PetArt nothing is cut out;
 * the empty border around the item is trimmed so its size and position are easy to work out.
 * Items without a picture return null, and the screens show their name instead.
 *
 * An item can also have a second picture made for wearing, named "<file> on pet.png"
 * (e.g. "glasses on pet.png": just the frames, without the arms the Shop picture shows).
 * onPet(item) uses that one if it exists, and the Shop picture if not.
 *
 *     BufferedImage img = CosmeticArt.of(crown);   // null if there's no picture yet
 *     PetArt.draw(g2, img, x, y, width, height);   // same drawing helper as the pets
 */
public final class CosmeticArt {

    private static final String FOLDER = "resources/images/";
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private static final BufferedImage MISSING = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    private CosmeticArt() { }

    /** Loads every item picture, so the Shop doesn't wait for them later. Called from PetArt's preload. */
    static void preload() {
        for (Cosmetic c : ShopCatalog.cosmetics()) {
            of(c);
            onPet(c);
        }
    }

    /** The item's picture for the Shop and the tiles, or null if there isn't one. */
    public static BufferedImage of(Cosmetic item) {
        return item == null ? null : load(fileName(item));
    }

    /** The picture to draw on the pet: "<file> on pet.png" if there is one, else the Shop picture. */
    public static BufferedImage onPet(Cosmetic item) {
        if (item == null) {
            return null;
        }
        BufferedImage worn = load(fileName(item) + " on pet");
        return worn != null ? worn : of(item);
    }

    /** "Party hat" -> "party-hat" */
    private static String fileName(Cosmetic item) {
        return item.getName().toLowerCase().replace(' ', '-');
    }

    private static synchronized BufferedImage load(String name) {
        BufferedImage cached = CACHE.get(name);
        if (cached == null) {
            cached = MISSING;
            for (String ext : new String[] {".png", ".PNG"}) {
                File f = new File(FOLDER + name + ext);
                if (f.isFile()) {
                    try {
                        BufferedImage raw = ImageIO.read(f);
                        if (raw != null) {
                            cached = trim(raw);
                        }
                    } catch (IOException e) {
                        System.err.println("Could not read " + f + ": " + e.getMessage());
                    }
                    break;
                }
            }
            CACHE.put(name, cached);
        }
        return cached == MISSING ? null : cached;
    }

    /** Cuts off the fully see-through border around the item. */
    private static BufferedImage trim(BufferedImage raw) {
        Raster alpha = raw.getAlphaRaster();
        if (alpha == null) {
            return raw; // no see-through parts at all
        }
        int w = raw.getWidth(), h = raw.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (alpha.getSample(x, y, 0) > 8) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        return maxX < 0 ? raw : raw.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
}
