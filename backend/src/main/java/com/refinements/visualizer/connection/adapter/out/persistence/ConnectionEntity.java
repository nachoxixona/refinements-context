package com.refinements.visualizer.connection.adapter.out.persistence;

import com.refinements.visualizer.connection.ConnectionEndpoints;
import com.refinements.visualizer.connection.ConnectionKind;
import com.refinements.visualizer.shared.Names;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Row of the {@code connection} table (V1__schema.sql). */
@Entity
@Table(name = "connection")
class ConnectionEntity {

    @Id
    UUID id;

    @Column(name = "name", nullable = false, length = Names.MAX_NAME)
    String name;

    @Column(name = "name_normalized", nullable = false, length = Names.MAX_NAME)
    String nameNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 10)
    ConnectionKind kind;

    @Column(name = "description", length = Names.MAX_DESCRIPTION)
    String description;

    @Column(name = "flow_id")
    UUID flowId;

    @Column(name = "step")
    Integer step;

    @Column(name = "endpoint", length = ConnectionEndpoints.MAX_LENGTH)
    String endpoint;

    @Column(name = "source_id")
    UUID sourceId;

    @Column(name = "target_id")
    UUID targetId;

    @Column(name = "source_name", nullable = false, length = Names.MAX_NAME)
    String sourceName;

    @Column(name = "target_name", nullable = false, length = Names.MAX_NAME)
    String targetName;

    @Column(name = "source_name_normalized", nullable = false, length = Names.MAX_NAME)
    String sourceNameNormalized;

    @Column(name = "target_name_normalized", nullable = false, length = Names.MAX_NAME)
    String targetNameNormalized;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected ConnectionEntity() {}

    ConnectionEntity(UUID id) {
        this.id = id;
    }
}
