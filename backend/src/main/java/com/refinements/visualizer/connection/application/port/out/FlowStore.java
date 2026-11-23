package com.refinements.visualizer.connection.application.port.out;

import com.refinements.visualizer.connection.domain.Flow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where flows are kept. */
public interface FlowStore {

    Optional<Flow> findById(UUID id);

    boolean existsById(UUID id);

    Optional<Flow> findByNameNormalized(String nameNormalized);

    List<Flow> findAll();

    /** Ordered by normalized name. */
    List<Flow> findAllOrdered();

    /** Writes the flow's state; flushed with the transaction. */
    void save(Flow flow);

    void saveAndFlush(Flow flow);

    /** Deletes and flushes. */
    void delete(UUID id);
}
