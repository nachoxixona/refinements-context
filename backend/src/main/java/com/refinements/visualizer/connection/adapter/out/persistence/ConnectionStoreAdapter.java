package com.refinements.visualizer.connection.adapter.out.persistence;

import com.refinements.visualizer.connection.ConnectionKey;
import com.refinements.visualizer.connection.application.port.out.ConnectionStore;
import com.refinements.visualizer.connection.domain.Connection;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link ConnectionStore} on Spring Data JPA. {@link #save} copies onto the managed entity (dirty checking writes
 * it at the next flush) or persists a new one (research R7).
 */
@Component
class ConnectionStoreAdapter implements ConnectionStore {

    private final ConnectionJpaRepository repository;
    private final EntityManager em;

    ConnectionStoreAdapter(ConnectionJpaRepository repository, EntityManager em) {
        this.repository = repository;
        this.em = em;
    }

    @Override
    public Optional<Connection> findById(UUID id) {
        return repository.findById(id).map(ConnectionMapper::toDomain);
    }

    @Override
    public List<Connection> findAll() {
        return map(repository.findAll());
    }

    @Override
    public List<Connection> findByFlowId(UUID flowId) {
        return map(repository.findByFlowId(flowId));
    }

    @Override
    public List<Connection> findAttachedTo(UUID microserviceId) {
        return map(repository.findBySourceIdOrTargetId(microserviceId, microserviceId));
    }

    @Override
    public List<Connection> findDanglingByName(String nameNormalized) {
        return map(repository.findDanglingByName(nameNormalized));
    }

    @Override
    public Optional<Connection> findByKey(ConnectionKey key) {
        return repository
                .findBySourceNameNormalizedAndTargetNameNormalizedAndNameNormalized(
                        key.source(), key.target(), key.name())
                .map(ConnectionMapper::toDomain);
    }

    @Override
    public void save(Connection connection) {
        ConnectionEntity managed = em.find(ConnectionEntity.class, connection.getId());
        if (managed != null) {
            ConnectionMapper.copy(connection, managed);
        } else {
            ConnectionEntity e = new ConnectionEntity(connection.getId());
            ConnectionMapper.copy(connection, e);
            em.persist(e);
        }
    }

    @Override
    public void saveAndFlush(Connection connection) {
        save(connection);
        em.flush();
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }

    @Override
    public void deleteAll(Collection<UUID> ids) {
        repository.deleteAllById(ids);
    }

    @Override
    public void flush() {
        em.flush();
    }

    private static List<Connection> map(List<ConnectionEntity> entities) {
        return entities.stream().map(ConnectionMapper::toDomain).toList();
    }
}
