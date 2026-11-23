package com.refinements.visualizer.microservice.spi;

import java.util.List;
import java.util.UUID;

/**
 * What the microservice module needs done to connection ends when a microservice is created, renamed or deleted.
 * Implemented by the connection module, so the compile-time dependency runs connection → microservice only
 * (research R4). Called synchronously inside the caller's transaction.
 */
public interface ConnectionEndsPort {

    /**
     * Detaches every connection end pointing at this microservice: keeps the stored end names, clears the ids and
     * flushes, before the microservice row is deleted. Returns the affected connection ids.
     */
    List<UUID> detach(UUID microserviceId);

    /** Updates the stored end names of connections attached to this microservice after a rename. */
    void renamed(UUID microserviceId, String newName);

    /**
     * Re-attaches dangling connection ends whose kept normalized name equals {@code nameNormalized}. Returns the ids
     * of the connections that had a matching dangling end.
     */
    List<UUID> reattach(UUID microserviceId, String name, String nameNormalized);
}
