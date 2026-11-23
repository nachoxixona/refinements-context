package com.refinements.visualizer.scenario.dto;

import java.util.List;

/** A baseline service or endpoint key the scenario references that no longer exists (FR-042). */
@io.swagger.v3.oas.annotations.media.Schema(name = "Orphan")
public record Orphan(String ref, List<String> usedBy) {}
