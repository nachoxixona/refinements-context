package com.refinements.visualizer.canvas.domain;

import com.refinements.visualizer.card.model.Field;
import com.refinements.visualizer.card.model.FieldLocation;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "Field")
public record FieldDto(
        String path,
        String name,
        String normalizedName,
        String type,
        String format,
        FieldLocation location,
        boolean required,
        Integer variant,
        boolean truncated) {

    public static FieldDto from(Field f) {
        return new FieldDto(
                f.path(),
                f.name(),
                f.normalizedName(),
                f.type(),
                f.format(),
                f.location(),
                f.required(),
                f.variant(),
                f.truncated());
    }
}
