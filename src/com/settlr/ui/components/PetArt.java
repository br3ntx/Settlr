package com.settlr.ui.components;

import com.settlr.model.Pet;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Pictures of the pets and the pet egg, from resources/images (e.g. dog.PNG for the Dog, egg.PNG).
 *
 * The pictures are drawn on a flat beige background, which would show as a beige box on our dark
 * screens. So when a picture is loaded, the background around the pet is made see-through and the
 * pet's soft shadow becomes a dark see-through shadow. Everything inside the pet's outline
 * (eyes, paws, a light-coloured belly) is kept. Each picture is worked out once and cached.
 *
 * To add a pet picture, save it as resources/images/<pet name>.png, e.g. hamster.png.
 * Pets without a picture return null, and the screens draw a placeholder instead.
 *
 *     BufferedImage img = PetArt.of(pet);           // null if there's no picture yet
 *     PetArt.draw(g2, img, x, y, width, height);    // fits it in the box, keeping its shape
 */
public final class PetArt {

    private static final String FOLDER = "resources/images/";
    private static final Map<String, BufferedImage> CACHE = new HashMap<>();
    private static final Map<String, BufferedImage> SCALED = new HashMap<>();
    private static final BufferedImage MISSING = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

    private PetArt() { }

    /** Loads every picture on a background thread, so screens don't wait for them later. */
    public static void preloadInBackground() {
        Thread t = new Thread(() -> {
            egg();
            for (Pet p : com.settlr.model.ShopCatalog.pets()) {
                of(p);
            }
        }, "pet-art");
        t.setDaemon(true);
        t.start();
    }

    /** The pet's picture, or null if there isn't one yet. */
    public static BufferedImage of(Pet pet) {
        return pet == null ? null : load(pet.getName().toLowerCase().replace(' ', '_'));
    }

    /** The pet egg's picture, or null if it's missing. */
    public static BufferedImage egg() {
        return load("egg");
    }

    /** Draws the picture as big as fits in the box, centered, keeping its shape. */
    public static void draw(Graphics2D g2, BufferedImage img, float x, float y, float w, float h) {
        if (img == null || w < 1 || h < 1) {
            return;
        }
        double scale = Math.min(w / img.getWidth(), h / img.getHeight());
        int dw = Math.max(1, (int) Math.round(img.getWidth() * scale));
        int dh = Math.max(1, (int) Math.round(img.getHeight() * scale));
        BufferedImage small = scaled(img, dw, dh);
        g2.drawImage(small, Math.round(x + (w - dw) / 2), Math.round(y + (h - dh) / 2), null);
    }

    // =====================================================================
    //  Loading
    // =====================================================================

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
                            cached = cutOut(raw);
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

    /** Smooth shrinking: halves the picture step by step, then does the last bit in one go. */
    private static synchronized BufferedImage scaled(BufferedImage img, int w, int h) {
        String key = System.identityHashCode(img) + ":" + w + "x" + h;
        BufferedImage done = SCALED.get(key);
        if (done != null) {
            return done;
        }
        BufferedImage cur = img;
        while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) {
            cur = resize(cur, cur.getWidth() / 2, cur.getHeight() / 2);
        }
        done = resize(cur, w, h);
        SCALED.put(key, done);
        return done;
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    // =====================================================================
    //  Removing the beige background
    // =====================================================================

    /**
     * 1. Find the background colour (the usual colour along the edges).
     * 2. Mark the "ink": pixels that clearly aren't background (outlines, eyes, white whiskers).
     * 3. Thicken the ink a little so the fuzzy outline has no gaps, fill everything it encloses,
     *    then thin it back. That's the pet; paws and bellies the same colour as the background
     *    stay because they're inside an outline.
     * 4. Everything else is background: fully clear, except darker bits (the shadow), which turn
     *    into a see-through dark shadow.
     */
    private static BufferedImage cutOut(BufferedImage raw) {
        int w = raw.getWidth(), h = raw.getHeight(), n = w * h;
        int[] px = raw.getRGB(0, 0, w, h, null, 0, w);
        float[] bg = borderColour(px, w, h);
        float bgLen = bg[0] * bg[0] + bg[1] * bg[1] + bg[2] * bg[2];

        float[] bright = new float[n]; // how light compared with the background (1 = same)
        boolean[] ink = new boolean[n];
        for (int i = 0; i < n; i++) {
            float r = (px[i] >> 16) & 255, g = (px[i] >> 8) & 255, b = px[i] & 255;
            float k = (r * bg[0] + g * bg[1] + b * bg[2]) / bgLen;
            float dr = r - k * bg[0], dg = g - k * bg[1], db = b - k * bg[2];
            float offColour = (float) Math.sqrt(dr * dr + dg * dg + db * db);
            bright[i] = k;
            ink[i] = offColour > 16 || k < 0.72f || k > 1.05f;
        }

        boolean[] thick = grow(ink, w, h, 3);
        boolean[] outside = floodFromEdges(thick, w, h);
        boolean[] filled = new boolean[n];
        for (int i = 0; i < n; i++) {
            filled[i] = !outside[i];
        }
        boolean[] pet = shrink(filled, w, h, 3);
        for (int i = 0; i < n; i++) {
            pet[i] |= ink[i];
        }
        dropSpecks(pet, w, h, n / 500);

        int[] out = new int[n];
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int i = 0; i < n; i++) {
            int a;
            if (pet[i]) {
                out[i] = px[i] | 0xFF000000;
                a = 255;
            } else {
                float shade = Math.max(0f, Math.min(1f, (0.99f - bright[i]) / 0.25f));
                a = Math.round(shade * 0.6f * 255);
                out[i] = (a << 24) | 0x0A1010; // dark teal shadow
            }
            if (a > 10) {
                int x = i % w, y = i / w;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        result.setRGB(0, 0, w, h, out, 0, w);
        if (maxX < 0) {
            return result;
        }
        return result.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    /** The median colour of the pixels along the four edges. */
    private static float[] borderColour(int[] px, int w, int h) {
        int count = 2 * w + 2 * h;
        int[][] ch = new int[3][count];
        int c = 0;
        for (int x = 0; x < w; x++) {
            for (int y : new int[] {0, h - 1}) {
                int p = px[y * w + x];
                ch[0][c] = (p >> 16) & 255;
                ch[1][c] = (p >> 8) & 255;
                ch[2][c++] = p & 255;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x : new int[] {0, w - 1}) {
                int p = px[y * w + x];
                ch[0][c] = (p >> 16) & 255;
                ch[1][c] = (p >> 8) & 255;
                ch[2][c++] = p & 255;
            }
        }
        float[] result = new float[3];
        for (int k = 0; k < 3; k++) {
            Arrays.sort(ch[k]);
            result[k] = Math.max(1, ch[k][count / 2]);
        }
        return result;
    }

    /** Thickens a mask by the given number of pixels (up, down, left, right each step). */
    private static boolean[] grow(boolean[] mask, int w, int h, int steps) {
        boolean[] cur = mask;
        for (int s = 0; s < steps; s++) {
            boolean[] next = cur.clone();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    if (!cur[i] && ((x > 0 && cur[i - 1]) || (x < w - 1 && cur[i + 1])
                            || (y > 0 && cur[i - w]) || (y < h - 1 && cur[i + w]))) {
                        next[i] = true;
                    }
                }
            }
            cur = next;
        }
        return cur;
    }

    /** Thins a mask by the given number of pixels. */
    private static boolean[] shrink(boolean[] mask, int w, int h, int steps) {
        boolean[] inverted = new boolean[mask.length];
        for (int i = 0; i < mask.length; i++) {
            inverted[i] = !mask[i];
        }
        boolean[] grown = grow(inverted, w, h, steps);
        boolean[] result = new boolean[mask.length];
        for (int i = 0; i < mask.length; i++) {
            result[i] = !grown[i];
        }
        return result;
    }

    /** Every pixel reachable from the picture's edges without crossing the wall mask. */
    private static boolean[] floodFromEdges(boolean[] wall, int w, int h) {
        boolean[] seen = new boolean[w * h];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int x = 0; x < w; x++) {
            visit(x, wall, seen, queue);
            visit((h - 1) * w + x, wall, seen, queue);
        }
        for (int y = 0; y < h; y++) {
            visit(y * w, wall, seen, queue);
            visit(y * w + w - 1, wall, seen, queue);
        }
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = i % w, y = i / w;
            if (x > 0) visit(i - 1, wall, seen, queue);
            if (x < w - 1) visit(i + 1, wall, seen, queue);
            if (y > 0) visit(i - w, wall, seen, queue);
            if (y < h - 1) visit(i + w, wall, seen, queue);
        }
        return seen;
    }

    private static void visit(int i, boolean[] wall, boolean[] seen, ArrayDeque<Integer> queue) {
        if (!wall[i] && !seen[i]) {
            seen[i] = true;
            queue.add(i);
        }
    }

    /** Removes tiny separate bits (stray dots in the background) smaller than minSize pixels. */
    private static void dropSpecks(boolean[] mask, int w, int h, int minSize) {
        int[] label = new int[mask.length];
        int next = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        java.util.List<Integer> members = new java.util.ArrayList<>();
        for (int start = 0; start < mask.length; start++) {
            if (!mask[start] || label[start] != 0) {
                continue;
            }
            next++;
            members.clear();
            label[start] = next;
            queue.add(start);
            while (!queue.isEmpty()) {
                int i = queue.poll();
                members.add(i);
                int x = i % w, y = i / w;
                int[] around = {x > 0 ? i - 1 : -1, x < w - 1 ? i + 1 : -1, y > 0 ? i - w : -1, y < h - 1 ? i + w : -1};
                for (int j : around) {
                    if (j >= 0 && mask[j] && label[j] == 0) {
                        label[j] = next;
                        queue.add(j);
                    }
                }
            }
            if (members.size() < minSize) {
                for (int i : members) {
                    mask[i] = false;
                }
            }
        }
    }
}
