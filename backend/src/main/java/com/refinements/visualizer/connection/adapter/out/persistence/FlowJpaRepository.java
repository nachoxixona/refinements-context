package com.refinements.visualizer.connection.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FlowJpaRepository extends JpaRepository<FlowEntity, UUID> {

    Optional<FlowEntity> findByNameNormalized(String nameNormalized);

    List<FlowEntity> findAllByOrderByNameNormalizedAsc();
}
