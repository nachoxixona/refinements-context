package com.refinements.visualizer.layout;

import java.util.UUID;

/** Keys of the baseline layout's position map (research R8 of 003): {@code ms:<id>} and {@code card:<id>}. */
public final class LayoutKeys {

    public static final String MS_PREFIX = "ms:";
    public static final String CARD_PREFIX = "card:";

    private LayoutKeys() {}

    public static String msKey(UUID id) {
        return MS_PREFIX + id;
    }

    public static String cardKey(UUID id) {
        return CARD_PREFIX + id;
    }
}
