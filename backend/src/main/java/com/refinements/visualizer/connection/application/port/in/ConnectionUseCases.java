package com.refinements.visualizer.connection.application.port.in;

import com.refinements.visualizer.connection.ConnectionKind;
import com.refinements.visualizer.connection.ConnectionView;
import java.util.UUID;

/** Connections drawn and edited on the canvas (FR-017, FR-018a, FR-024, FR-027). Every write bumps the revision. */
public interface ConnectionUseCases {

    record NewConnection(
            UUID sourceId,
            UUID targetId,
            String name,
            ConnectionKind kind,
            String description,
            UUID flowId,
            String endpoint,
            Integer step) {}

    /**
     * A partial change: {@code name} null leaves the name; for the other properties the {@code *Present} flag says
     * whether to change them, and a null value clears them.
     */
    record ConnectionChanges(
            String name,
            ConnectionKind kind,
            boolean kindPresent,
            String description,
            boolean descriptionPresent,
            UUID flowId,
            boolean flowPresent,
            String endpoint,
            boolean endpointPresent,
            Integer step,
            boolean stepPresent) {}

    /** The connection after a write and the new workspace revision. */
    record Mutation(ConnectionView connection, long revision) {}

    Mutation create(NewConnection connection);

    Mutation update(UUID id, ConnectionChanges changes);

    void delete(UUID id);

    /** Points a dangling end at an existing microservice (FR-027); at least one of the ids is required. */
    Mutation reassign(UUID id, UUID sourceId, UUID targetId);
}
