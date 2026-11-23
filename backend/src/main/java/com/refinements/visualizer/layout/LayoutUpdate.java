package com.refinements.visualizer.layout;

import com.refinements.visualizer.common.XYPosition;
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
