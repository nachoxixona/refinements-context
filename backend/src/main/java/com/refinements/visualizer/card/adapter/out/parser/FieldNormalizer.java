package com.refinements.visualizer.card.adapter.out.parser;

import java.util.Locale;

/** Lower-case and strip {@code _}, {@code -} and whitespace (research R4).  Used for search over field names. */
public final class FieldNormalizer {

    private FieldNormalizer() {}

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(name.length());
        for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c != '_' && c != '-' && !Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
