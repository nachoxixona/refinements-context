package com.refinements.visualizer.layout;

import com.refinements.visualizer.shared.XYPosition;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only view of the baseline layout for other modules (contracts/module-apis.md §layout). */
public record LayoutView(Map<String, XYPosition> positions, List<UUID> collapsedCards, int version) {}
