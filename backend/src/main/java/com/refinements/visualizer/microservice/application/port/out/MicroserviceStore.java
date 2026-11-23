package com.refinements.visualizer.microservice.application.port.out;

import com.refinements.visualizer.microservice.domain.Microservice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where microservices are kept. */
public interface MicroserviceStore {

    Optional<Microservice> findById(UUID id);

    Optional<Microservice> findByNameNormalized(String nameNormalized);

    Optional<Microservice> findBySpecCardId(UUID cardId);

    List<Microservice> findAll();

    /** Ordered by normalized name. */
    List<Microservice> findAllOrdered();

    /** Inserts or updates, and flushes. */
    void save(Microservice microservice);

    void delete(UUID id);
}
