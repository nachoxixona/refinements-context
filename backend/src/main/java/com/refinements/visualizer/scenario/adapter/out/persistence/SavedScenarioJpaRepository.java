package com.refinements.visualizer.scenario.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SavedScenarioJpaRepository extends JpaRepository<SavedScenarioEntity, UUID> {

    boolean existsByNameNormalized(String nameNormalized);

    boolean existsByNameNormalizedAndIdNot(String nameNormalized, UUID id);

    List<SavedScenarioEntity> findAllByOrderByUpdatedAtDesc();
}
