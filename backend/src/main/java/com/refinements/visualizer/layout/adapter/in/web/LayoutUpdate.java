package com.refinements.visualizer.layout.adapter.in.web;

import com.refinements.visualizer.shared.XYPosition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record LayoutUpdate(
        @NotNull Integer version,
        @NotNull Map<String, @Valid @NotNull XYPosition> positions,
        List<@NotNull UUID> collapsedCards) {

    public LayoutUpdate {
        collapsedCards = collapsedCards == null ? List.of() : List.copyOf(collapsedCards);
    }
}
