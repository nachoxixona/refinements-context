package com.refinements.visualizer.scenario.dto;

import com.refinements.visualizer.scenario.SavedScenario;
import java.time.Instant;
import java.util.UUID;

@io.swagger.v3.oas.annotations.media.Schema(name = "ScenarioSummary")
public record ScenarioSummary(
        UUID id, String name, String description, int version, Instant createdAt, Instant updatedAt) {

    public static ScenarioSummary from(SavedScenario s) {
        return new ScenarioSummary(
                s.getId(), s.getName(), s.getDescription(), s.getVersion(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
