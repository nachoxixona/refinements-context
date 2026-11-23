package com.refinements.visualizer.scenario.application.port.in;

import com.refinements.visualizer.scenario.domain.BaselineRef;
import com.refinements.visualizer.scenario.domain.Orphan;
import com.refinements.visualizer.scenario.domain.ScenarioContent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Saved scenarios (FR-036 – FR-043). Reads the baseline only: never writes microservices, cards, connections or the
 * layout, and never bumps the workspace revision (Principle IV, FR-039).
 */
public interface ScenarioUseCases {

    record ScenarioSummary(
            UUID id, String name, String description, int version, Instant createdAt, Instant updatedAt) {}

    /** A scenario with what it references that no longer exists and the cards that changed since it was drawn. */
    record ScenarioDetails(
            UUID id,
            String name,
            String description,
            int version,
            Instant createdAt,
            Instant updatedAt,
            ScenarioContent content,
            List<Orphan> orphans,
            List<UUID> baselineDrift) {}

    /** {@code version} is the optimistic-lock version the caller loaded; required on update. */
    record ScenarioWrite(String name, String description, ScenarioContent content, Integer version) {}

    /** A parsed scenario document; {@code baselineRef} null means "the baseline as it is now". */
    record ImportedScenario(String name, String description, BaselineRef baselineRef, ScenarioContent content) {}

    record ExportedScenario(
            String name, String description, Instant exportedAt, BaselineRef baselineRef, ScenarioContent content) {}

    List<ScenarioSummary> list();

    ScenarioDetails get(UUID id);

    ScenarioDetails create(ScenarioWrite write);

    ScenarioDetails update(UUID id, ScenarioWrite write);

    void delete(UUID id);

    /** Saves an imported scenario under a free name ("… (imported)", "… (imported 2)" on a clash). */
    ScenarioDetails importScenario(ImportedScenario scenario);

    ExportedScenario export(UUID id);
}
