package com.refinements.visualizer.connection;

import java.util.Locale;

/**
 * data-model.md §3b Flow.color: a fixed palette without green and red, which mark proposed and removed
 * elements in draft mode (FR-049). Written lowercase in the connections document.
 */
public enum FlowColor {
    BLUE,
    PURPLE,
    ORANGE,
    PINK,
    AMBER,
    BROWN,
    CYAN,
    SLATE;

    public String documentValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Lowercase document value → enum; anything else → null. */
    public static FlowColor fromDocument(String value) {
        for (FlowColor c : values()) {
            if (c.documentValue().equals(value)) {
                return c;
            }
        }
        return null;
    }
}
