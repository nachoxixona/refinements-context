package com.refinements.visualizer.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "XYPosition")
public record XYPosition(@NotNull Double x, @NotNull Double y) {}
