package com.refinements.visualizer.microservice.domain;

import com.refinements.visualizer.shared.Names;
import com.refinements.visualizer.shared.errors.BadRequestException;
import java.time.Instant;
import java.util.UUID;

/** A named node on the canvas, with or without a spec (data-model.md §2). Plain Java. */
public class Microservice {

    private final UUID id;
    private String name;
    private String nameNormalized;
    private String description;
    private String team;
    private String repositoryUrl;
    private String docsUrl;
    private UUID specCardId;
    private final Instant createdAt;
    private Instant updatedAt;

    /** A new microservice; {@code name} and {@code description} must already pass {@link #checkName}/{@link #checkDescription}. */
    public static Microservice create(String name, String description, Instant now) {
        Microservice m = new Microservice(UUID.randomUUID(), null, null, description, null, null, null, null, now, now);
        m.rename(name, now);
        return m;
    }

    /** Rebuilds a stored microservice (persistence adapter only). */
    public static Microservice restore(
            UUID id,
            String name,
            String nameNormalized,
            String description,
            String team,
            String repositoryUrl,
            String docsUrl,
            UUID specCardId,
            Instant createdAt,
            Instant updatedAt) {
        return new Microservice(
                id, name, nameNormalized, description, team, repositoryUrl, docsUrl, specCardId, createdAt, updatedAt);
    }

    private Microservice(
            UUID id,
            String name,
            String nameNormalized,
            String description,
            String team,
            String repositoryUrl,
            String docsUrl,
            UUID specCardId,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.nameNormalized = nameNormalized;
        this.description = description;
        this.team = team;
        this.repositoryUrl = repositoryUrl;
        this.docsUrl = docsUrl;
        this.specCardId = specCardId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Microservice.name rule (data-model.md §2): cleaned, non-empty, at most {@link Names#MAX_NAME}. */
    public static String checkName(String name) {
        String clean = Names.clean(name);
        if (clean.isEmpty()) {
            throw new BadRequestException("A microservice needs a name.");
        }
        if (clean.length() > Names.MAX_NAME) {
            throw new BadRequestException("Microservice names are at most " + Names.MAX_NAME + " characters.");
        }
        return clean;
    }

    /** Microservice.description rule (data-model.md §2): at most {@link Names#MAX_DESCRIPTION}. */
    public static void checkDescription(String description) {
        if (description != null && description.length() > Names.MAX_DESCRIPTION) {
            throw new BadRequestException("Descriptions are at most " + Names.MAX_DESCRIPTION + " characters.");
        }
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

    /** Sets ownership; values must already pass {@link Ownership}. */
    public void own(String team, String repositoryUrl, String docsUrl, Instant now) {
        this.team = team;
        this.repositoryUrl = repositoryUrl;
        this.docsUrl = docsUrl;
        this.updatedAt = now;
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

    public String getTeam() {
        return team;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public String getDocsUrl() {
        return docsUrl;
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
