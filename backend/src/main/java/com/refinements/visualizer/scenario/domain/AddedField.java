package com.refinements.visualizer.scenario.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** A proposed request (IN) or response (OUT) field on an existing card endpoint; nested by path. */
@Schema(name = "AddedField")
public record AddedField(
        @NotNull UUID cardId,
        @NotBlank String endpointKey,
        @NotNull Direction direction,
        @NotNull @Valid SimpleField field) {

    public enum Direction {
        IN,
        OUT
    }
}
