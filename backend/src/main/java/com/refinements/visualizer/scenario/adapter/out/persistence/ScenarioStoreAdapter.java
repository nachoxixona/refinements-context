package com.refinements.visualizer.scenario.adapter.out.persistence;

import com.refinements.visualizer.scenario.application.port.out.ScenarioStore;
import com.refinements.visualizer.scenario.domain.Scenario;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link ScenarioStore} on Spring Data JPA. {@link #save} copies onto the managed entity (or persists a new one),
 * flushes, and returns the scenario with the version Hibernate assigned (research R7).
 */
@Component
class ScenarioStoreAdapter implements ScenarioStore {

    private final SavedScenarioJpaRepository repository;
    private final EntityManager em;

    ScenarioStoreAdapter(SavedScenarioJpaRepository repository, EntityManager em) {
        this.repository = repository;
        this.em = em;
    }

    @Override
    public Optional<Scenario> findById(UUID id) {
        return repository.findById(id).map(ScenarioMapper::toDomain);
    }

    @Override
    public List<Scenario> findAllNewestFirst() {
        return repository.findAllByOrderByUpdatedAtDesc().stream()
                .map(ScenarioMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByNameNormalized(String nameNormalized) {
        return repository.existsByNameNormalized(nameNormalized);
    }

    @Override
    public boolean existsByNameNormalizedAndIdNot(String nameNormalized, UUID id) {
        return repository.existsByNameNormalizedAndIdNot(nameNormalized, id);
    }

    @Override
    public Scenario save(Scenario scenario) {
        SavedScenarioEntity e = em.find(SavedScenarioEntity.class, scenario.getId());
        if (e == null) {
            e = new SavedScenarioEntity(scenario.getId());
            ScenarioMapper.copy(scenario, e);
            em.persist(e);
        } else {
            ScenarioMapper.copy(scenario, e);
        }
        em.flush();
        return ScenarioMapper.toDomain(e);
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }
}
