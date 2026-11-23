package com.refinements.visualizer.connection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowRepository extends JpaRepository<Flow, UUID> {

    Optional<Flow> findByNameNormalized(String nameNormalized);

    List<Flow> findAllByOrderByNameNormalizedAsc();
}
