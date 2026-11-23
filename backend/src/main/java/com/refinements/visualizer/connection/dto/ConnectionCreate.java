package com.refinements.visualizer.connection.dto;

import com.refinements.visualizer.connection.ConnectionKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(name = "ConnectionCreate")
public record ConnectionCreate(
        @NotNull UUID sourceId,
        @NotNull UUID targetId,
        @NotBlank @Size(max = 120) String name,
        ConnectionKind kind,
        @Size(max = 2000) String description,
        UUID flowId,
        @Size(max = 600) String endpoint,
        @Schema(
                        minimum = "1",
                        nullable = true,
                        description = "Order in the flow; appended when absent (spec 004 FR-002)")
                Integer step) {

    /** Without a step (callers from before spec 004). */
    public ConnectionCreate(
            UUID sourceId,
            UUID targetId,
            String name,
            ConnectionKind kind,
            String description,
            UUID flowId,
            String endpoint) {
        this(sourceId, targetId, name, kind, description, flowId, endpoint, null);
    }
}
