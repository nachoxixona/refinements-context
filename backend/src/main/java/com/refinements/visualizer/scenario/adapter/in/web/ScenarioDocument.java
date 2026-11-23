package com.refinements.visualizer.scenario.adapter.in.web;

import com.refinements.visualizer.scenario.domain.BaselineRef;
import com.refinements.visualizer.scenario.domain.ScenarioContent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** Portable export format, formatVersion 3 (FR-041). */
@Schema(name = "ScenarioDocument")
public record ScenarioDocument(
        @NotNull Integer formatVersion,
        @NotBlank String name,
        String description,
        Instant exportedAt,
        BaselineRef baselineRef,
        @NotNull @Valid ScenarioContent content) {

    public static final int FORMAT_VERSION = 3;
}
