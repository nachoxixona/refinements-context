package com.refinements.visualizer.card.application.port.out;

import com.refinements.visualizer.card.domain.SpecCard;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Where spec cards are kept. */
public interface SpecCardStore {

    Optional<SpecCard> findById(UUID id);

    Optional<SpecCard> findFirstByContentHash(String contentHash);

    Optional<SpecCard> findFirstBySourceUrl(String sourceUrl);

    /** Newest first (createdAt descending). */
    List<SpecCard> findAllNewestFirst();

    List<SpecCard> findAll();

    boolean exists(UUID id);

    Set<UUID> allIds();

    /** Inserts or updates. */
    void save(SpecCard card);

    void delete(UUID id);
}
