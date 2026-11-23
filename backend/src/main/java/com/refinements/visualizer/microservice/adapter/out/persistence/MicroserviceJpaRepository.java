package com.refinements.visualizer.microservice.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MicroserviceJpaRepository extends JpaRepository<MicroserviceEntity, UUID> {

    Optional<MicroserviceEntity> findByNameNormalized(String nameNormalized);

    Optional<MicroserviceEntity> findBySpecCardId(UUID specCardId);

    List<MicroserviceEntity> findAllByOrderByNameNormalizedAsc();
}
