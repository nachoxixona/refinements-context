package com.refinements.visualizer.scenario.model;

import java.util.Map;
import java.util.UUID;

/** data-model.md §7 SavedScenario.baselineRef: card hashes and the workspace revision at save time. */
public record BaselineRef(Map<UUID, String> cards, Long revision) {

    public BaselineRef {
        cards = cards == null ? Map.of() : Map.copyOf(cards);
    }
}
