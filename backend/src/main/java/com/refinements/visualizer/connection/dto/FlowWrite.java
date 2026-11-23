package com.refinements.visualizer.connection.dto;

import com.refinements.visualizer.connection.FlowColor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "FlowWrite")
public record FlowWrite(@NotBlank @Size(max = 120) String name, @NotNull FlowColor color) {}
