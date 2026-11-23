package com.refinements.visualizer.scenario.model;

import com.refinements.visualizer.connection.ConnectionKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code source} and {@code target} are baseline microservice ids or proposed tempKeys. */
@Schema(name = "ProposedConnection")
public record ProposedConnection(
        @NotBlank String id,
        @NotBlank String source,
        @NotBlank String target,
        @NotBlank @Size(max = 120) String name,
        ConnectionKind kind,
        @Size(max = 2000) String description,
        java.util.UUID flowId,
        @Size(max = 600) String endpoint,
        @jakarta.validation.constraints.Min(1) Integer step) {

    public ProposedConnection(
            String id,
            String source,
            String target,
            String name,
            ConnectionKind kind,
            String description,
            java.util.UUID flowId,
            String endpoint) {
        this(id, source, target, name, kind, description, flowId, endpoint, null);
    }
}
