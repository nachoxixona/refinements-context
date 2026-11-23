package com.refinements.visualizer.connection;

import com.refinements.visualizer.card.model.SpecKeys;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** data-model.md §3c Connection.endpoint: {@code METHOD /path} of an endpoint on the connection's target (FR-054). */
public final class ConnectionEndpoints {

    public static final int MAX_LENGTH = 600;

    private static final Set<String> METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "TRACE");
    private static final Pattern FORMAT = Pattern.compile("^\\s*([A-Za-z]+)\\s+(/\\S*)\\s*$");

    private ConnectionEndpoints() {}

    /** The stored form (method upper-cased, single space), or null when {@code value} is not valid. */
    public static String normalize(String value) {
        if (value == null || value.length() > MAX_LENGTH) {
            return null;
        }
        Matcher m = FORMAT.matcher(value);
        if (!m.matches()) {
            return null;
        }
        String method = m.group(1).toUpperCase(Locale.ROOT);
        return METHODS.contains(method) ? method + " " + m.group(2) : null;
    }

    /** The comparison key: method plus normalized path, so {@code /orders/{id}} matches {@code /orders/{orderId}}. */
    public static String matchKey(String method, String path) {
        return method.toUpperCase(Locale.ROOT) + " " + SpecKeys.normalizePath(path);
    }

    /** {@link #matchKey} of a stored endpoint value. */
    public static String matchKey(String endpoint) {
        int space = endpoint.indexOf(' ');
        return matchKey(endpoint.substring(0, space), endpoint.substring(space + 1));
    }
}
