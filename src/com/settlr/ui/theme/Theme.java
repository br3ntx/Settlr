package com.settlr.ui.theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;

/**
 * Every color and font Settlr uses, in one place.
 * Change a value here and every screen updates; never type a hex code inside a screen.
 */
public final class Theme {

    private Theme() { } // only constants, so nobody should create a Theme object

    // ---- Palette (from Figma) ----
public static final Color PRIMARY       = new Color(0xE8D9CD); // beige: buttons, selected sidebar item
public static final Color PRIMARY_DARK  = new Color(0xD4C3B5); // beige when hovered/pressed
public static final Color ON_PRIMARY    = new Color(0x1F2B2B); // NEW: text on beige buttons
public static final Color ACCENT        = new Color(0xF2B630); // trophy gold (leaderboard)
public static final Color WARNING       = new Color(0xE8862A);
public static final Color DANGER        = new Color(0xE0605A); // a bit brighter so it shows on dark
public static final Color BACKGROUND    = new Color(0x223030); // page background
public static final Color SIDEBAR       = new Color(0x192525); // NEW: darker sidebar
public static final Color SURFACE = new Color(0x293535); // cards: same look as 60% #2D3939 on the background, but solid (see-through colors leave old text behind in Swing)
public static final Color INPUT_BACKGROUND = new Color(0x2C3838); // NEW: inside text fields
public static final Color BORDER        = new Color(0x3A4747); // card outlines
public static final Color INPUT_BORDER  = new Color(0x6B7A7A); // field outlines
public static final Color TEXT          = new Color(0xF2F2EF); // main text (light)
public static final Color MUTED_TEXT    = new Color(0x8A9797); // placeholders, labels

    // ---- Mint (Figma "Add expenses" button, selected member chips) ----
public static final Color MINT          = new Color(0x5FD0B0);
public static final Color MINT_DARK     = new Color(0x4FB89A); // hovered/pressed
public static final Color ON_MINT       = new Color(0x173030); // text on mint
public static final Color OVERLAY       = new Color(10, 18, 18, 165); // dims the app behind a popup

    // ---- Glass card (Figma: inner shadow, inner glow, light edge) ----
public static final Color GLASS_FILL    = new Color(0x2C3838);
public static final Color GLASS_SHADOW  = new Color(0, 0, 0, 38);       // dark rim, top + left
public static final Color GLASS_GLOW    = new Color(255, 255, 255, 9);  // light rim, bottom + right
public static final Color GLASS_EDGE    = new Color(255, 255, 255, 52); // thin outline

    // ---- Fonts ----
    // Uses Inter if it is installed, otherwise Segoe UI (on every Windows PC).
    // Later, FontManager can load Inter from resources/fonts so it works everywhere.
    private static final String FONT_NAME = pickInstalledFont("Inter", "Segoe UI");

    /** Example: Theme.font(Font.BOLD, 20) */
    public static Font font(int style, int size) {
        return new Font(FONT_NAME, style, size);
    }

    private static String pickInstalledFont(String... wanted) {
        String[] installed = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                                .getAvailableFontFamilyNames();
        for (String name : wanted) {
            for (String have : installed) {
                if (have.equalsIgnoreCase(name)) {
                    return have;
                }
            }
        }
        return Font.SANS_SERIF; // Java's built-in fallback
    }
}
