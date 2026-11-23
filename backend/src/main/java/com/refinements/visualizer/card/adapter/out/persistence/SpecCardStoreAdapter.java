package com.refinements.visualizer.card.adapter.out.persistence;

import com.refinements.visualizer.card.application.port.out.SpecCardStore;
import com.refinements.visualizer.card.domain.SpecCard;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link SpecCardStore} on Spring Data JPA. {@link #save} copies the domain state onto the loaded entity (or a new
 * one); it works both inside a transaction and without one, as the card use cases need (research R7).
 */
@Component
class SpecCardStoreAdapter implements SpecCardStore {

    private final SpecCardJpaRepository repository;

    SpecCardStoreAdapter(SpecCardJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<SpecCard> findById(UUID id) {
        return repository.findById(id).map(SpecCardMapper::toDomain);
    }

    @Override
    public Optional<SpecCard> findFirstByContentHash(String contentHash) {
        return repository.findFirstByContentHash(contentHash).map(SpecCardMapper::toDomain);
    }

    @Override
    public Optional<SpecCard> findFirstBySourceUrl(String sourceUrl) {
        return repository.findFirstBySourceUrl(sourceUrl).map(SpecCardMapper::toDomain);
    }

    @Override
    public List<SpecCard> findAllNewestFirst() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(SpecCardMapper::toDomain)
                .toList();
    }

    @Override
    public List<SpecCard> findAll() {
        return repository.findAll().stream().map(SpecCardMapper::toDomain).toList();
    }

    @Override
    public boolean exists(UUID id) {
        return repository.existsById(id);
    }

    @Override
    public Set<UUID> allIds() {
        return Set.copyOf(repository.findAllIds());
    }

    @Override
    public void save(SpecCard card) {
        SpecCardEntity e = repository.findById(card.getId()).orElseGet(() -> new SpecCardEntity(card.getId()));
        SpecCardMapper.copy(card, e);
        repository.save(e);
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
    }
}
