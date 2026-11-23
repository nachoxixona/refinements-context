package com.refinements.visualizer.connection;

import java.util.Locale;

/** data-model.md §3 Connection.kind. Written lowercase ({@code request}, {@code event}) in the connections document. */
public enum ConnectionKind {
    REQUEST,
    EVENT;

    public String documentValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** {@code request}/{@code event} → enum; anything else → null. */
    public static ConnectionKind fromDocument(String value) {
        if ("request".equals(value)) {
            return REQUEST;
        }
        if ("event".equals(value)) {
            return EVENT;
        }
        return null;
    }
}
