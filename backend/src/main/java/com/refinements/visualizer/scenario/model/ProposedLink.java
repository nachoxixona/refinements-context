package com.refinements.visualizer.scenario.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** {@code microservice} is a baseline microservice id or a proposed {@code tempKey}. */
@Schema(name = "ProposedLink")
public record ProposedLink(@NotBlank String microservice, @NotNull UUID cardId) {}
