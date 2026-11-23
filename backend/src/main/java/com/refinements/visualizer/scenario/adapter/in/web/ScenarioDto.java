package com.refinements.visualizer.scenario.adapter.in.web;

import com.refinements.visualizer.scenario.application.port.in.ScenarioUseCases.ScenarioDetails;
import com.refinements.visualizer.scenario.domain.Orphan;
import com.refinements.visualizer.scenario.domain.ScenarioContent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "Scenario")
public record ScenarioDto(
        UUID id,
        String name,
        String description,
        int version,
        Instant createdAt,
        Instant updatedAt,
        ScenarioContent content,
        List<Orphan> orphans,
        List<UUID> baselineDrift) {

    static ScenarioDto from(ScenarioDetails d) {
        return new ScenarioDto(
                d.id(),
                d.name(),
                d.description(),
                d.version(),
                d.createdAt(),
                d.updatedAt(),
                d.content(),
                d.orphans(),
                d.baselineDrift());
    }
}
