package com.refinements.visualizer.microservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Row of the {@code microservice} table (V1__schema.sql). */
@Entity
@Table(name = "microservice")
class MicroserviceEntity {

    @Id
    UUID id;

    @Column(name = "name", nullable = false, length = 120)
    String name;

    @Column(name = "name_normalized", nullable = false, length = 120, unique = true)
    String nameNormalized;

    @Column(name = "description", length = 2000)
    String description;

    @Column(name = "team", length = 120)
    String team;

    @Column(name = "repository_url", length = 2048)
    String repositoryUrl;

    @Column(name = "docs_url", length = 2048)
    String docsUrl;

    @Column(name = "spec_card_id", unique = true)
    UUID specCardId;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected MicroserviceEntity() {}

    MicroserviceEntity(UUID id) {
        this.id = id;
    }
}
