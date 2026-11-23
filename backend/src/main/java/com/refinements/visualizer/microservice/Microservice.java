package com.refinements.visualizer.microservice;

import com.refinements.visualizer.shared.Names;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A named node on the canvas, with or without a spec (data-model.md §2). */
@Entity
@Table(name = "microservice")
public class Microservice {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = Names.MAX_NAME)
    private String name;

    @Column(name = "name_normalized", nullable = false, length = Names.MAX_NAME, unique = true)
    private String nameNormalized;

    @Column(name = "description", length = Names.MAX_DESCRIPTION)
    private String description;

    /** spec 004 data-model.md §2 Microservice.team, Microservice.repositoryUrl, Microservice.docsUrl (FR-013). */
    @Column(name = "team", length = Ownership.MAX_TEAM)
    private String team;

    @Column(name = "repository_url", length = Ownership.MAX_URL)
    private String repositoryUrl;

    @Column(name = "docs_url", length = Ownership.MAX_URL)
    private String docsUrl;

    @Column(name = "spec_card_id", unique = true)
    private UUID specCardId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Microservice() {}

    public Microservice(String name, String description, Instant now) {
        this.id = UUID.randomUUID();
        rename(name, now);
        this.description = description;
        this.createdAt = now;
    }

    public void rename(String name, Instant now) {
        this.name = Names.clean(name);
        this.nameNormalized = Names.norm(name);
        this.updatedAt = now;
    }

    public void describe(String description, Instant now) {
        this.description = description;
        this.updatedAt = now;
    }

    public void own(String team, String repositoryUrl, String docsUrl, Instant now) {
        this.team = team;
        this.repositoryUrl = repositoryUrl;
        this.docsUrl = docsUrl;
        this.updatedAt = now;
    }

    public String getTeam() {
        return team;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getDocsUrl() {
        return docsUrl;
    }

    public void link(UUID cardId, Instant now) {
        this.specCardId = cardId;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameNormalized() {
        return nameNormalized;
    }

    public String getDescription() {
        return description;
    }

    public UUID getSpecCardId() {
        return specCardId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
