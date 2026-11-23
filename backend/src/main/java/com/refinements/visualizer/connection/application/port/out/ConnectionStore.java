package com.refinements.visualizer.connection.application.port.out;

import com.refinements.visualizer.connection.ConnectionKey;
import com.refinements.visualizer.connection.domain.Connection;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Where connections are kept. {@link #save} writes the connection's state but leaves flushing to the
 * transaction, {@link #saveAndFlush} and {@link #flush} write it at once; callers keep the flush order the
 * unique (source, target, name) key and the microservice delete need (data-model.md "Behaviours whose ordering
 * must be preserved").
 */
public interface ConnectionStore {

    Optional<Connection> findById(UUID id);

    List<Connection> findAll();

    List<Connection> findByFlowId(UUID flowId);

    /** Connections with an end on {@code microserviceId}. */
    List<Connection> findAttachedTo(UUID microserviceId);

    /** Connections with a dangling end named {@code nameNormalized}. */
    List<Connection> findDanglingByName(String nameNormalized);

    Optional<Connection> findByKey(ConnectionKey key);

    void save(Connection connection);

    void saveAndFlush(Connection connection);

    void delete(UUID id);

    void deleteAll(Collection<UUID> ids);

    void flush();
}
