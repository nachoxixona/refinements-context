package com.refinements.visualizer.shared;

import com.fasterxml.jackson.databind.JsonNode;
import com.refinements.visualizer.shared.errors.BadRequestException;
import java.util.Iterator;
import java.util.Set;

/** Reads a JSON merge-style PATCH body where "absent" and "null" differ. Unknown properties are rejected. */
public final class PatchBody {

    private final JsonNode node;

    private PatchBody(JsonNode node) {
        this.node = node;
    }

    public static PatchBody of(JsonNode body, String... allowed) {
        if (body == null || !body.isObject() || body.isEmpty()) {
            throw new BadRequestException("The request body must be an object with at least one property.");
        }
        Set<String> names = Set.of(allowed);
        for (Iterator<String> it = body.fieldNames(); it.hasNext(); ) {
            String f = it.next();
            if (!names.contains(f)) {
                throw new BadRequestException("Unknown property '" + f + "'.");
            }
        }
        return new PatchBody(body);
    }

    public boolean has(String field) {
        return node.has(field);
    }

    /** The string value, or null when absent or null; 400 when present with another type. */
    public String text(String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (!v.isTextual()) {
            throw new BadRequestException("'" + field + "' must be a string.");
        }
        return v.asText();
    }
}
