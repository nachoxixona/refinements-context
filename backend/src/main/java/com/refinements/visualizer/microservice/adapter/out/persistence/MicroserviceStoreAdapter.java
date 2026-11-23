package com.refinements.visualizer.microservice.adapter.out.persistence;

import com.refinements.visualizer.microservice.application.port.out.MicroserviceStore;
import com.refinements.visualizer.microservice.domain.Microservice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link MicroserviceStore} on Spring Data JPA. {@link #save} copies onto the managed entity (or a new one) and
 * flushes, as the old {@code saveAndFlush} calls did (research R7).
 */
@Component
class MicroserviceStoreAdapter implements MicroserviceStore {

    private final MicroserviceJpaRepository repository;

    MicroserviceStoreAdapter(MicroserviceJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Microservice> findById(UUID id) {
        return repository.findById(id).map(MicroserviceMapper::toDomain);
    }

    @Override
    public Optional<Microservice> findByNameNormalized(String nameNormalized) {
        return repository.findByNameNormalized(nameNormalized).map(MicroserviceMapper::toDomain);
    }

    @Override
    public Optional<Microservice> findBySpecCardId(UUID cardId) {
        return repository.findBySpecCardId(cardId).map(MicroserviceMapper::toDomain);
    }

    @Override
    public List<Microservice> findAll() {
        return repository.findAll().stream().map(MicroserviceMapper::toDomain).toList();
    }

    @Override
    public List<Microservice> findAllOrdered() {
        return repository.findAllByOrderByNameNormalizedAsc().stream()
                .map(MicroserviceMapper::toDomain)
                .toList();
    }

    @Override
    public void save(Microservice microservice) {
        MicroserviceEntity e =
                repository.findById(microservice.getId()).orElseGet(() -> new MicroserviceEntity(microservice.getId()));
        MicroserviceMapper.copy(microservice, e);
        repository.saveAndFlush(e);
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }
}
