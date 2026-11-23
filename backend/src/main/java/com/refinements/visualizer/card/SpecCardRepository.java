package com.refinements.visualizer.card;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SpecCardRepository extends JpaRepository<SpecCard, UUID> {

    Optional<SpecCard> findFirstByContentHash(String contentHash);

    Optional<SpecCard> findFirstBySourceUrl(String sourceUrl);

    List<SpecCard> findAllByOrderByCreatedAtDesc();

    @Query("select c.id from SpecCard c")
    List<UUID> findAllIds();
}
