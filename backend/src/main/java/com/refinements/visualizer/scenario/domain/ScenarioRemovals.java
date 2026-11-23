package com.refinements.visualizer.scenario.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** Baseline connections, microservices and links (microservice ids) proposed for removal (FR-037a). */
@Schema(name = "ScenarioRemovals")
public record ScenarioRemovals(List<UUID> connections, List<UUID> microservices, List<UUID> links) {

    public static final ScenarioRemovals EMPTY = new ScenarioRemovals(null, null, null);

    public ScenarioRemovals {
        connections = connections == null ? List.of() : List.copyOf(connections);
        microservices = microservices == null ? List.of() : List.copyOf(microservices);
        links = links == null ? List.of() : List.copyOf(links);
    }
}
