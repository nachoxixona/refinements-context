package com.refinements.visualizer.card.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SpecCardJpaRepository extends JpaRepository<SpecCardEntity, UUID> {

    Optional<SpecCardEntity> findFirstByContentHash(String contentHash);

    Optional<SpecCardEntity> findFirstBySourceUrl(String sourceUrl);

    List<SpecCardEntity> findAllByOrderByCreatedAtDesc();

    @Query("select c.id from SpecCardEntity c")
    List<UUID> findAllIds();
}
