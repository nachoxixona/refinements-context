package com.refinements.visualizer.card.model;

/**
 * A flattened request or response field (data-model.md Field). {@code path} is a dot path with
 * {@code []} for arrays, {@code type} falls back to {@code unknown}, {@code truncated} is set when the
 * depth or cycle limit stopped expansion.
 */
public record Field(
        String path,
        String name,
        String normalizedName,
        String type,
        String format,
        FieldLocation location,
        boolean required,
        Integer variant,
        boolean truncated) {

    public Field {
        if (type == null || type.isBlank()) {
            type = "unknown";
        }
    }
}
