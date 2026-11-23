package com.refinements.visualizer.connection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConnectionRepository extends JpaRepository<Connection, UUID> {

    List<Connection> findBySourceIdOrTargetId(UUID sourceId, UUID targetId);

    List<Connection> findByFlowId(UUID flowId);

    @Query("select f from Connection f where (f.sourceId is null and f.sourceNameNormalized = ?1)"
            + " or (f.targetId is null and f.targetNameNormalized = ?1)")
    List<Connection> findDanglingByName(String nameNormalized);

    Optional<Connection> findBySourceNameNormalizedAndTargetNameNormalizedAndNameNormalized(
            String source, String target, String name);

    default Optional<Connection> findByKey(ConnectionKey key) {
        return findBySourceNameNormalizedAndTargetNameNormalizedAndNameNormalized(
                key.source(), key.target(), key.name());
    }
}
