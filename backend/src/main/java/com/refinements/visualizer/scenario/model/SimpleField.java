package com.refinements.visualizer.scenario.model;

import jakarta.validation.constraints.NotBlank;

@io.swagger.v3.oas.annotations.media.Schema(name = "SimpleField")
public record SimpleField(@NotBlank String name, String type) {}
