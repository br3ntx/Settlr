package com.settlr.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Picture handling for profile photos. Kept in util so the services don't depend on the
 * AWT/ImageIO graphics libraries: ProfileService only says "make a square PNG of this file".
 */
public final class ImageUtil {

    private ImageUtil() { }

    /**
     * Crops the middle square of the picture, shrinks it to size x size and saves it as a PNG.
     * Returns false if the file isn't a picture Java can open (nothing is saved then).
     * Throws IOException if the PNG could not be written.
     */
    public static boolean saveSquarePng(File source, Path target, int size) throws IOException {
        BufferedImage image;
        try {
            image = ImageIO.read(source);
        } catch (IOException e) {
            image = null;
        }
        if (image == null) {
            return false;
        }
        ImageIO.write(squareThumbnail(image, size), "png", target.toFile());
        return true;
    }

    /** The middle square of the picture, scaled to size x size. */
    private static BufferedImage squareThumbnail(BufferedImage src, int size) {
        int side = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - side) / 2, y = (src.getHeight() - side) / 2;
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, size, size, x, y, x + side, y + side, null);
        g.dispose();
        return out;
    }
}
