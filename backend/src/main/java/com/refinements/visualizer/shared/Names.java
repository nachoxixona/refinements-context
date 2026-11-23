package com.refinements.visualizer.shared;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Name normalization for {@code Microservice.nameNormalized} and the {@code Connection} name keys (data-model.md §2,
 * §3): lower case, trimmed, internal whitespace collapsed to one space (FR-011).
 */
public final class Names {

    public static final int MAX_NAME = 120;
    public static final int MAX_DESCRIPTION = 2000;

    private static final Pattern SPACES = Pattern.compile("\\s+");

    private Names() {}

    public static String norm(String name) {
        if (name == null) {
            return "";
        }
        return SPACES.matcher(name.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
    }

    /** The display form that is stored: trimmed, internal whitespace collapsed, case kept. */
    public static String clean(String name) {
        return name == null ? "" : SPACES.matcher(name.trim()).replaceAll(" ");
    }
}
