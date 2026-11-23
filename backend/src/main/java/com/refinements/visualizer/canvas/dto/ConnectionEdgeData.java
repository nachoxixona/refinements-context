package com.refinements.visualizer.canvas.dto;

import com.refinements.visualizer.connection.ConnectionKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "ConnectionEdgeData")
public record ConnectionEdgeData(
        UUID connectionId,
        String name,
        ConnectionKind kind,
        String description,
        UUID flowId,
        String endpoint,
        @Schema(description = "true when the target has a spec card that does not contain the endpoint (FR-055)")
                boolean endpointMissing,
        @Schema(nullable = true, description = "Order within its flow (spec 004 FR-001)") Integer step) {}
