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

    // ---- Palette (from the "Design starter" section of the plan doc) ----
    public static final Color PRIMARY       = new Color(0x1F7A68); // Settlr Teal
    public static final Color PRIMARY_DARK  = new Color(0x155A4C); // hover / pressed
    public static final Color ACCENT        = new Color(0xF2B630); // Trophy Gold
    public static final Color WARNING       = new Color(0xE8862A); // Late Amber
    public static final Color DANGER        = new Color(0xC9443F); // Utang Coral
    public static final Color BACKGROUND    = new Color(0xF7F5F0); // app background
    public static final Color SURFACE       = Color.WHITE;         // cards, dialogs
    public static final Color BORDER        = new Color(0xE3DED4); // card borders
    public static final Color INPUT_BORDER  = new Color(0xBDB6A8); // text field outline
    public static final Color TEXT          = new Color(0x1D2627);
    public static final Color MUTED_TEXT    = new Color(0x6B7474);

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
