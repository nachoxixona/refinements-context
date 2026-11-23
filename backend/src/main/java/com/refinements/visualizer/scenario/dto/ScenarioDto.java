package com.refinements.visualizer.scenario.dto;

import com.refinements.visualizer.scenario.SavedScenario;
import com.refinements.visualizer.scenario.model.ScenarioContent;
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

    public static ScenarioDto from(SavedScenario s, List<Orphan> orphans, List<UUID> drift) {
        return new ScenarioDto(
                s.getId(),
                s.getName(),
                s.getDescription(),
                s.getVersion(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                s.getContent(),
                orphans,
                drift);
    }
}
