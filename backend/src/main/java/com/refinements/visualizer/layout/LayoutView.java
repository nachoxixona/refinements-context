package com.refinements.visualizer.layout;

import com.refinements.visualizer.shared.XYPosition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only view of the baseline layout for other modules (contracts/module-apis.md §layout). */
public record LayoutView(Map<String, XYPosition> positions, List<UUID> collapsedCards, int version) {

    static LayoutView from(BaselineLayout l) {
        return new LayoutView(
                Collections.unmodifiableMap(new LinkedHashMap<>(l.getPositions())),
                List.copyOf(l.getCollapsedCards()),
                l.getVersion());
    }
}
