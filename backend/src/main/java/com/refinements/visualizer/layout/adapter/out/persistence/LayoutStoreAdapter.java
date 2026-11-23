package com.refinements.visualizer.layout.adapter.out.persistence;

import com.refinements.visualizer.layout.application.port.out.LayoutStore;
import com.refinements.visualizer.layout.domain.BaselineLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;

/**
 * {@link LayoutStore} on Spring Data JPA. {@link #save} copies onto the entity loaded by {@link #get()} in the same
 * transaction, so Hibernate's version check still compares against the version the request was based on.
 */
@Component
class LayoutStoreAdapter implements LayoutStore {

    private final BaselineLayoutJpaRepository repository;

    LayoutStoreAdapter(BaselineLayoutJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public BaselineLayout get() {
        BaselineLayoutEntity e = entity();
        return new BaselineLayout(e.positions, e.collapsedCards, e.version, e.updatedAt);
    }

    @Override
    public int save(BaselineLayout layout) {
        BaselineLayoutEntity e = entity();
        e.positions = new LinkedHashMap<>(layout.getPositions());
        e.collapsedCards = new ArrayList<>(layout.getCollapsedCards());
        e.updatedAt = layout.getUpdatedAt();
        return repository.saveAndFlush(e).version;
    }

    private BaselineLayoutEntity entity() {
        return repository
                .findById(BaselineLayoutEntity.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("baseline_layout row 1 is missing"));
    }
}
