package com.refinements.visualizer.scenario.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * A named Draft Scenario drawn over the baseline (data-model.md §7, content v2). {@code version} is the optimistic
 * lock version; the store assigns it on every save.
 */
public class Scenario {

    public static final int MAX_NAME = 120;

    private final UUID id;
    private String name;
    private String nameNormalized;
    private String description;
    private ScenarioContent content;
    private BaselineRef baselineRef;
    private final int version;
    private final Instant createdAt;
    private Instant updatedAt;

    /** A new scenario; give it a name and content with {@link #update} before saving. */
    public static Scenario create(Instant now) {
        return new Scenario(UUID.randomUUID(), null, null, null, null, new BaselineRef(null, null), 0, now, now);
    }

    /** Rebuilds a stored scenario (persistence adapter only). */
    public static Scenario restore(
            UUID id,
            String name,
            String nameNormalized,
            String description,
            ScenarioContent content,
            BaselineRef baselineRef,
            int version,
            Instant createdAt,
            Instant updatedAt) {
        return new Scenario(id, name, nameNormalized, description, content, baselineRef, version, createdAt, updatedAt);
    }

    private Scenario(
            UUID id,
            String name,
            String nameNormalized,
            String description,
            ScenarioContent content,
            BaselineRef baselineRef,
            int version,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.nameNormalized = nameNormalized;
        this.description = description;
        this.content = content;
        this.baselineRef = baselineRef;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** {@code lower(trim(name))}: the unique key of a scenario name. */
    public static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    public void update(String name, String description, ScenarioContent content, BaselineRef baselineRef, Instant now) {
        this.name = name.trim();
        this.nameNormalized = normalizeName(name);
        this.description = description;
        this.content = content;
        this.baselineRef = baselineRef;
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

    public ScenarioContent getContent() {
        return content;
    }

    public BaselineRef getBaselineRef() {
        return baselineRef;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
