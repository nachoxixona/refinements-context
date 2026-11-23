package com.refinements.visualizer.scenario.adapter.in.web;

import com.refinements.visualizer.scenario.domain.ScenarioContent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Body of POST and PUT. {@code version} is required on PUT (optimistic locking). */
@io.swagger.v3.oas.annotations.media.Schema(name = "ScenarioWrite")
public record ScenarioWrite(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @NotNull @Valid ScenarioContent content,
        Integer version) {}
