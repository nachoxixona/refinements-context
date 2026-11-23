package com.refinements.visualizer.scenario.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** A new endpoint proposed on a baseline spec card. */
@Schema(name = "AddedEndpoint")
public record AddedEndpoint(@NotNull UUID cardId, @NotNull @Valid ProposedEndpoint endpoint) {}
