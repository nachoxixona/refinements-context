package com.refinements.visualizer.connection.adapter.out.persistence;

import com.refinements.visualizer.connection.application.port.out.FlowStore;
import com.refinements.visualizer.connection.domain.Flow;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** {@link FlowStore} on Spring Data JPA, with the same save semantics as {@link ConnectionStoreAdapter}. */
@Component
class FlowStoreAdapter implements FlowStore {

    private final FlowJpaRepository repository;
    private final EntityManager em;

    FlowStoreAdapter(FlowJpaRepository repository, EntityManager em) {
        this.repository = repository;
        this.em = em;
    }

    @Override
    public Optional<Flow> findById(UUID id) {
        return repository.findById(id).map(ConnectionMapper::toDomain);
    }

    @Override
    public boolean existsById(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Optional<Flow> findByNameNormalized(String nameNormalized) {
        return repository.findByNameNormalized(nameNormalized).map(ConnectionMapper::toDomain);
    }

    @Override
    public List<Flow> findAll() {
        return repository.findAll().stream().map(ConnectionMapper::toDomain).toList();
    }

    @Override
    public List<Flow> findAllOrdered() {
        return repository.findAllByOrderByNameNormalizedAsc().stream()
                .map(ConnectionMapper::toDomain)
                .toList();
    }

    @Override
    public void save(Flow flow) {
        FlowEntity managed = em.find(FlowEntity.class, flow.getId());
        if (managed != null) {
            ConnectionMapper.copy(flow, managed);
        } else {
            FlowEntity e = new FlowEntity(flow.getId());
            ConnectionMapper.copy(flow, e);
            em.persist(e);
        }
    }

    @Override
    public void saveAndFlush(Flow flow) {
        save(flow);
        em.flush();
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
        em.flush();
    }
}
