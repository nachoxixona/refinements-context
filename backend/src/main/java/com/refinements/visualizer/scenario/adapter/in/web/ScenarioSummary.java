package com.refinements.visualizer.scenario.adapter.in.web;

import com.refinements.visualizer.scenario.application.port.in.ScenarioUseCases;
import java.time.Instant;
import java.util.UUID;

@io.swagger.v3.oas.annotations.media.Schema(name = "ScenarioSummary")
public record ScenarioSummary(
        UUID id, String name, String description, int version, Instant createdAt, Instant updatedAt) {

    static ScenarioSummary from(ScenarioUseCases.ScenarioSummary s) {
        return new ScenarioSummary(s.id(), s.name(), s.description(), s.version(), s.createdAt(), s.updatedAt());
    }
}
