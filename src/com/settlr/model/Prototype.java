package com.settlr.model;

// DESIGN PATTERN: Prototype (interface) - anything that can make a copy of itself with copy().
/**
 * Something that can make a copy of itself (Prototype pattern).
 *
 * Used where a screen lets you change something and then Save or Cancel: the screen changes a
 * copy, so Cancel just throws the copy away and the original is untouched. For example,
 * Edit pet works on copies of the household's pets (OwnedPet.copy()).
 */
public interface Prototype<T> {

    /** A new object with the same values, that can be changed without changing this one. */
    T copy();
}
