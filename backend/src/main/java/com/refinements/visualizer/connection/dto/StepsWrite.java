package com.refinements.visualizer.connection.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** {@code PUT /api/flows/{flowId}/steps}: every connection of the flow with its step (spec 004 FR-004). */
@Schema(name = "StepsWrite")
public record StepsWrite(@NotNull List<Step> steps) {

    @Schema(name = "StepAssignment")
    public record Step(@NotNull UUID connectionId, @NotNull Integer step) {}
}
