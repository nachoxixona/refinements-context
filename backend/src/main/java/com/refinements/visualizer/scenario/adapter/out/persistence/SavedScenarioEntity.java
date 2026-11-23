package com.refinements.visualizer.scenario.adapter.out.persistence;

import com.refinements.visualizer.scenario.domain.BaselineRef;
import com.refinements.visualizer.scenario.domain.ScenarioContent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Row of the {@code saved_scenario} table (V1__schema.sql); content and baselineRef are JSON columns. */
@Entity
@Table(name = "saved_scenario")
class SavedScenarioEntity {

    @Id
    UUID id;

    @Column(name = "name", nullable = false, length = 120)
    String name;

    /** {@code lower(trim(name))}; unique. */
    @Column(name = "name_normalized", nullable = false, length = 120, unique = true)
    String nameNormalized;

    @Column(name = "description", length = 2000)
    String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false)
    ScenarioContent content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "baseline_ref", nullable = false)
    BaselineRef baselineRef;

    /** Optimistic lock; Hibernate assigns it, the mapper never copies it onto the entity. */
    @Version
    @Column(name = "version", nullable = false)
    int version;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected SavedScenarioEntity() {}

    SavedScenarioEntity(UUID id) {
        this.id = id;
    }
}
