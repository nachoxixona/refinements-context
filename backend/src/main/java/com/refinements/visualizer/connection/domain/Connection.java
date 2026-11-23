package com.refinements.visualizer.connection.domain;

import com.refinements.visualizer.connection.ConnectionKey;
import com.refinements.visualizer.connection.ConnectionKind;
import com.refinements.visualizer.connection.ConnectionView;
import com.refinements.visualizer.shared.Names;
import java.time.Instant;
import java.util.UUID;

/**
 * A named, directed interaction from one microservice to another (data-model.md §3). The ends are nullable
 * microservice ids plus the microservice names; a null id with a kept name is a dangling end (research R2).
 */
public class Connection {

    private final UUID id;
    private String name;
    private String nameNormalized;
    private ConnectionKind kind;
    private String description;
    /** data-model.md §3b: the flow, or null. */
    private UUID flowId;
    /** spec 004 data-model.md §1 Connection.step: order within its flow, null without a flow (FR-001). */
    private Integer step;
    /** data-model.md §3c: {@code METHOD /path} on the target, or null. */
    private String endpoint;

    private UUID sourceId;
    private UUID targetId;
    private String sourceName;
    private String targetName;
    private String sourceNameNormalized;
    private String targetNameNormalized;
    private final Instant createdAt;
    private Instant updatedAt;

    /** A new connection without ends; set them with {@link #setSource} and {@link #setTarget}. */
    public Connection(String name, ConnectionKind kind, String description, Instant now) {
        this.id = UUID.randomUUID();
        this.createdAt = now;
        rename(name, now);
        this.kind = kind;
        this.description = description;
    }

    private Connection(UUID id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    /** Rebuilds a stored connection (persistence adapter only). */
    public static Connection restore(
            UUID id,
            String name,
            String nameNormalized,
            ConnectionKind kind,
            String description,
            UUID flowId,
            Integer step,
            String endpoint,
            UUID sourceId,
            UUID targetId,
            String sourceName,
            String targetName,
            String sourceNameNormalized,
            String targetNameNormalized,
            Instant createdAt,
            Instant updatedAt) {
        Connection c = new Connection(id, createdAt);
        c.name = name;
        c.nameNormalized = nameNormalized;
        c.kind = kind;
        c.description = description;
        c.flowId = flowId;
        c.step = step;
        c.endpoint = endpoint;
        c.sourceId = sourceId;
        c.targetId = targetId;
        c.sourceName = sourceName;
        c.targetName = targetName;
        c.sourceNameNormalized = sourceNameNormalized;
        c.targetNameNormalized = targetNameNormalized;
        c.updatedAt = updatedAt;
        return c;
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

    public void setStep(Integer step, Instant now) {
        this.step = step;
        this.updatedAt = now;
    }

    public void setEndpoint(String endpoint, Instant now) {
        this.endpoint = endpoint;
        this.updatedAt = now;
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

    /**
     * The microservice {@code microserviceId} is going away: its ends keep their names and lose the id.
     *
     * @return whether this connection had an end on it
     */
    public boolean detach(UUID microserviceId, Instant now) {
        boolean touched = false;
        if (microserviceId.equals(sourceId)) {
            setSource(null, sourceName, now);
            touched = true;
        }
        if (microserviceId.equals(targetId)) {
            setTarget(null, targetName, now);
            touched = true;
        }
        return touched;
    }

    /** The microservice {@code microserviceId} was renamed: its ends take the new name. */
    public void endRenamed(UUID microserviceId, String newName, Instant now) {
        if (microserviceId.equals(sourceId)) {
            setSource(microserviceId, newName, now);
        }
        if (microserviceId.equals(targetId)) {
            setTarget(microserviceId, newName, now);
        }
    }

    /**
     * Dangling ends named {@code nameNormalized} point at {@code microserviceId} again (FR-027).
     *
     * @return whether an end was reattached
     */
    public boolean reattach(UUID microserviceId, String name, String nameNormalized, Instant now) {
        boolean touched = false;
        if (sourceId == null && sourceNameNormalized.equals(nameNormalized)) {
            setSource(microserviceId, name, now);
            touched = true;
        }
        if (targetId == null && targetNameNormalized.equals(nameNormalized)) {
            setTarget(microserviceId, name, now);
            touched = true;
        }
        return touched;
    }

    /** The R3 identity: (source, target, name), all normalized. */
    public ConnectionKey key() {
        return new ConnectionKey(sourceNameNormalized, targetNameNormalized, nameNormalized);
    }

    public boolean isDangling() {
        return sourceId == null || targetId == null;
    }

    public ConnectionView view() {
        return new ConnectionView(
                id,
                name,
                kind,
                description,
                flowId,
                endpoint,
                step,
                sourceId,
                targetId,
                sourceName,
                targetName,
                isDangling());
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

    public UUID getFlowId() {
        return flowId;
    }

    public Integer getStep() {
        return step;
    }

    public String getEndpoint() {
        return endpoint;
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
