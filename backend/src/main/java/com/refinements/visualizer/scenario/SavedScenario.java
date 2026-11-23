package com.refinements.visualizer.scenario;

import com.refinements.visualizer.scenario.model.BaselineRef;
import com.refinements.visualizer.scenario.model.ScenarioContent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A named Draft Scenario drawn over the baseline (data-model.md §7, content v2). */
@Entity
@Table(name = "saved_scenario")
public class SavedScenario {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** {@code lower(trim(name))}, set by the app on every write; unique. */
    @Column(name = "name_normalized", nullable = false, length = 120, unique = true)
    private String nameNormalized;

    @Column(name = "description", length = 2000)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false)
    private ScenarioContent content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "baseline_ref", nullable = false)
    private BaselineRef baselineRef = new BaselineRef(null, null);

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SavedScenario() {}

    public SavedScenario(Instant now) {
        this.id = UUID.randomUUID();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static String normalizeName(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    @PrePersist
    @PreUpdate
    void normalize() {
        this.nameNormalized = normalizeName(name);
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
