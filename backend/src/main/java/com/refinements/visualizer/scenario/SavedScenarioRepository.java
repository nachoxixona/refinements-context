package com.refinements.visualizer.scenario;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedScenarioRepository extends JpaRepository<SavedScenario, UUID> {

    boolean existsByNameNormalized(String nameNormalized);

    boolean existsByNameNormalizedAndIdNot(String nameNormalized, UUID id);

    List<SavedScenario> findAllByOrderByUpdatedAtDesc();
}
