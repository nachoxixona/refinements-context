package com.refinements.visualizer.connection.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ConnectionJpaRepository extends JpaRepository<ConnectionEntity, UUID> {

    List<ConnectionEntity> findBySourceIdOrTargetId(UUID sourceId, UUID targetId);

    List<ConnectionEntity> findByFlowId(UUID flowId);

    @Query("select f from ConnectionEntity f where (f.sourceId is null and f.sourceNameNormalized = ?1)"
            + " or (f.targetId is null and f.targetNameNormalized = ?1)")
    List<ConnectionEntity> findDanglingByName(String nameNormalized);

    Optional<ConnectionEntity> findBySourceNameNormalizedAndTargetNameNormalizedAndNameNormalized(
            String source, String target, String name);
}
