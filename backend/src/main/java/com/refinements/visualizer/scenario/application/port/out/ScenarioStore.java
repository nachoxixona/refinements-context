package com.refinements.visualizer.scenario.application.port.out;

import com.refinements.visualizer.scenario.domain.Scenario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where scenarios are kept. */
public interface ScenarioStore {

    Optional<Scenario> findById(UUID id);

    /** Most recently updated first. */
    List<Scenario> findAllNewestFirst();

    boolean existsByNameNormalized(String nameNormalized);

    boolean existsByNameNormalizedAndIdNot(String nameNormalized, UUID id);

    /** Inserts or updates and flushes; returns the scenario with the version the store assigned. */
    Scenario save(Scenario scenario);

    void delete(UUID id);
}
