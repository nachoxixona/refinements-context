package com.refinements.visualizer.connection;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A named, directed interaction from one microservice to another (data-model.md §3). The ends are nullable
 * foreign keys plus the microservice names; a null id with a kept name is a dangling end (research R2).
 */
@Entity
@Table(name = "connection")
public class Connection {

    @Id
    private UUID id;

    @Column(name = "name", nullable = false, length = Names.MAX_NAME)
    private String name;

    @Column(name = "name_normalized", nullable = false, length = Names.MAX_NAME)
    private String nameNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 10)
    private ConnectionKind kind;

    @Column(name = "description", length = Names.MAX_DESCRIPTION)
    private String description;

    /** data-model.md §3b: nullable FK to flow, ON DELETE SET NULL. */
    @Column(name = "flow_id")
    private UUID flowId;

    /** data-model.md §3c: {@code METHOD /path} on the target, or null. */
    /** spec 004 data-model.md §1 Connection.step: order within its flow, null without a flow (FR-001). */
    @Column(name = "step")
    private Integer step;

    @Column(name = "endpoint", length = ConnectionEndpoints.MAX_LENGTH)
    private String endpoint;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "source_name", nullable = false, length = Names.MAX_NAME)
    private String sourceName;

    @Column(name = "target_name", nullable = false, length = Names.MAX_NAME)
    private String targetName;

    @Column(name = "source_name_normalized", nullable = false, length = Names.MAX_NAME)
    private String sourceNameNormalized;

    @Column(name = "target_name_normalized", nullable = false, length = Names.MAX_NAME)
    private String targetNameNormalized;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Connection() {}

    public Connection(String name, ConnectionKind kind, String description, Instant now) {
        this.id = UUID.randomUUID();
        this.createdAt = now;
        rename(name, now);
        this.kind = kind;
        this.description = description;
    }

    public void rename(String name, Instant now) {
        this.name = Names.clean(name);
        this.nameNormalized = Names.norm(name);
        this.updatedAt = now;
    }

    public void change(ConnectionKind kind, String description, Instant now) {
        this.kind = kind;
        this.description = description;
        this.updatedAt = now;
    }

    public void setFlow(UUID flowId, Instant now) {
        this.flowId = flowId;
        this.updatedAt = now;
    }

    public UUID getFlowId() {
        return flowId;
    }

    public void setStep(Integer step, Instant now) {
        this.step = step;
        this.updatedAt = now;
    }

    public Integer getStep() {
        return step;
    }

    public void setEndpoint(String endpoint, Instant now) {
        this.endpoint = endpoint;
        this.updatedAt = now;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setSource(UUID id, String name, Instant now) {
        this.sourceId = id;
        this.sourceName = Names.clean(name);
        this.sourceNameNormalized = Names.norm(name);
        this.updatedAt = now;
    }

    public void setTarget(UUID id, String name, Instant now) {
        this.targetId = id;
        this.targetName = Names.clean(name);
        this.targetNameNormalized = Names.norm(name);
        this.updatedAt = now;
    }

    /** The R3 identity: (source, target, name), all normalized. */
    public ConnectionKey key() {
        return new ConnectionKey(sourceNameNormalized, targetNameNormalized, nameNormalized);
    }

    public boolean isDangling() {
        return sourceId == null || targetId == null;
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

    public ConnectionKind getKind() {
        return kind;
    }

    public String getDescription() {
        return description;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getTargetName() {
        return targetName;
    }

    public String getSourceNameNormalized() {
        return sourceNameNormalized;
    }

    public String getTargetNameNormalized() {
        return targetNameNormalized;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
