package com.refinements.visualizer.microservice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MicroserviceRepository extends JpaRepository<Microservice, UUID> {

    Optional<Microservice> findByNameNormalized(String nameNormalized);

    Optional<Microservice> findBySpecCardId(UUID specCardId);

    List<Microservice> findAllByOrderByNameNormalizedAsc();
}
