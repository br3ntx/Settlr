package com.settlr.model;

/**
 * Something in the Shop. Two kinds (inheritance): a Cosmetic you buy with XP, and a Pet
 * you can only get from the pet crate. Items are fixed in ShopCatalog; the database only
 * stores their code next to who owns them.
 */
public abstract class ShopItem {

    private final String code;
    private final String name;

    protected ShopItem(String code, String name) {
        if (code == null || !code.matches("[A-Z_]{3,30}")) {
            throw new IllegalArgumentException("Item code must be 3 to 30 capital letters or underscores.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name is required.");
        }
        this.code = code;
        this.name = name;
    }

    /** Stored in owned_items.item_code, e.g. "HAT_CROWN". */
    public String getCode() { return code; }

    public String getName() { return name; }

    /** The small tag on the item's bubble, e.g. "HAT" or "RARE". */
    public abstract String getTag();
}
