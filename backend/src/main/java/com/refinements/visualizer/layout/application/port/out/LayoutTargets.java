package com.refinements.visualizer.layout.application.port.out;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** What exists on the canvas, so the layout can prune keys of deleted things (other modules behind it). */
public interface LayoutTargets {

    /** Microservice id → linked card id ({@code null} when it has none), for every microservice. */
    Map<UUID, UUID> microserviceCards();

    Set<UUID> cardIds();
}
