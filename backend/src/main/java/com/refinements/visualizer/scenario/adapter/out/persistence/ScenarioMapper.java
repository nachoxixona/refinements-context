package com.refinements.visualizer.scenario.adapter.out.persistence;

import com.refinements.visualizer.scenario.domain.Scenario;

/** Scenario ↔ SavedScenarioEntity. */
final class ScenarioMapper {

    private ScenarioMapper() {}

    static Scenario toDomain(SavedScenarioEntity e) {
        return Scenario.restore(
                e.id,
                e.name,
                e.nameNormalized,
                e.description,
                e.content,
                e.baselineRef,
                e.version,
                e.createdAt,
                e.updatedAt);
    }

    /** Copies the scenario's state onto a (new or managed) entity; the version stays Hibernate's. */
    static void copy(Scenario s, SavedScenarioEntity e) {
        e.name = s.getName();
        e.nameNormalized = s.getNameNormalized();
        e.description = s.getDescription();
        e.content = s.getContent();
        e.baselineRef = s.getBaselineRef();
        e.createdAt = s.getCreatedAt();
        e.updatedAt = s.getUpdatedAt();
    }
}
