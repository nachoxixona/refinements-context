package com.refinements.visualizer.connection.domain;

import com.refinements.visualizer.connection.FlowColor;
import com.refinements.visualizer.connection.FlowView;
import com.refinements.visualizer.shared.Names;
import java.time.Instant;
import java.util.UUID;

/** A named color that connections can belong to (data-model.md §3b, FR-049). */
public class Flow {

    private final UUID id;
    private String name;
    private String nameNormalized;
    private FlowColor color;
    private final Instant createdAt;
    private Instant updatedAt;

    public Flow(String name, FlowColor color, Instant now) {
        this.id = UUID.randomUUID();
        this.createdAt = now;
        rename(name, now);
        this.color = color;
    }

    private Flow(UUID id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    /** Rebuilds a stored flow (persistence adapter only). */
    public static Flow restore(
            UUID id, String name, String nameNormalized, FlowColor color, Instant createdAt, Instant updatedAt) {
        Flow f = new Flow(id, createdAt);
        f.name = name;
        f.nameNormalized = nameNormalized;
        f.color = color;
        f.updatedAt = updatedAt;
        return f;
    }

    public void rename(String name, Instant now) {
        this.name = Names.clean(name);
        this.nameNormalized = Names.norm(name);
        this.updatedAt = now;
    }

    public void recolor(FlowColor color, Instant now) {
        this.color = color;
        this.updatedAt = now;
    }

    public FlowView view() {
        return new FlowView(id, name, color);
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

    public FlowColor getColor() {
        return color;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
