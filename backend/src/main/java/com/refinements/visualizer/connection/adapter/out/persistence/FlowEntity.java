package com.refinements.visualizer.connection.adapter.out.persistence;

import com.refinements.visualizer.connection.FlowColor;
import com.refinements.visualizer.shared.Names;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Row of the {@code flow} table (V1__schema.sql). */
@Entity
@Table(name = "flow")
class FlowEntity {

    @Id
    UUID id;

    @Column(name = "name", nullable = false, length = Names.MAX_NAME)
    String name;

    @Column(name = "name_normalized", nullable = false, length = Names.MAX_NAME, unique = true)
    String nameNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "color", nullable = false, length = 10)
    FlowColor color;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected FlowEntity() {}

    FlowEntity(UUID id) {
        this.id = id;
    }
}
