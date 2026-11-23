package com.refinements.visualizer.connection;

import com.refinements.visualizer.shared.Names;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A named color that connections can belong to (data-model.md §3b, FR-049). */
@Entity
@Table(name = "flow")
public class Flow {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = Names.MAX_NAME)
    private String name;

    @Column(name = "name_normalized", nullable = false, length = Names.MAX_NAME, unique = true)
    private String nameNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "color", nullable = false, length = 10)
    private FlowColor color;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Flow() {}

    public Flow(String name, FlowColor color, Instant now) {
        this.id = UUID.randomUUID();
        this.createdAt = now;
        rename(name, now);
        this.color = color;
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
}
