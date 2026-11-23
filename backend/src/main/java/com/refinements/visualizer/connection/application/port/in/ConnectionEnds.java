package com.refinements.visualizer.connection.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Connection ends following microservice changes (research R4). Runs in the caller's transaction; offered to the
 * microservice module through {@code microservice.spi.ConnectionEndsPort}.
 */
public interface ConnectionEnds {

    /** Keeps the end names, clears the ids, flushes; returns the affected connection ids. */
    List<UUID> detach(UUID microserviceId);

    /** Ends on the microservice take its new name. */
    void renamed(UUID microserviceId, String newName);

    /** Dangling ends named {@code nameNormalized} point at the microservice again; returns their connection ids. */
    List<UUID> reattach(UUID microserviceId, String name, String nameNormalized);
}
