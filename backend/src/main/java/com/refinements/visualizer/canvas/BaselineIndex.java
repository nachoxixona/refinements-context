package com.refinements.visualizer.canvas;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What exists in the baseline right now, for scenario orphan and drift checks (FR-042). {@code microservices}
 * maps id → normalized name and {@code linkedCards} microservice id → card id.
 */
public record BaselineIndex(
        Map<UUID, String> microservices,
        Map<UUID, UUID> linkedCards,
        Map<UUID, String> cardHashes,
        Set<String> endpointKeys,
        Map<UUID, ConnectionRef> connections,
        Set<UUID> flows,
        long revision) {

    /** The R3 key parts of a baseline connection. */
    public record ConnectionRef(String source, String target, String name) {}
}
