package com.refinements.visualizer.scenario.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A proposed change to a baseline connection; null properties are unchanged, {@code kind = NONE} removes the kind, and
 * {@code flowId} is a flow id or {@code NONE} to ungroup (FR-053).
 */
@Schema(name = "ConnectionChange")
public record ConnectionChange(
        @NotNull UUID connectionId,
        @Size(min = 1, max = 120) String name,
        Kind kind,
        @Size(max = 2000) String description,
        String flowId,
        @Size(max = 600) String endpoint,
        @Schema(nullable = true, description = "Proposed step in its flow; null = unchanged (spec 004 FR-007)")
                @jakarta.validation.constraints.Min(1)
                Integer step) {

    public ConnectionChange(
            UUID connectionId, String name, Kind kind, String description, String flowId, String endpoint) {
        this(connectionId, name, kind, description, flowId, endpoint, null);
    }

    public static final String NO_FLOW = "NONE";
    public static final String NO_ENDPOINT = "NONE";

    public enum Kind {
        REQUEST,
        EVENT,
        NONE
    }
}
