package com.refinements.visualizer.connection;

import java.util.UUID;

/**
 * A connection as other modules see it. Same fields as the published REST schema {@code Connection}; a connection
 * is dangling when either end has no microservice id (its stored name is kept).
 */
public record ConnectionView(
        UUID id,
        String name,
        ConnectionKind kind,
        String description,
        UUID flowId,
        String endpoint,
        Integer step,
        UUID sourceId,
        UUID targetId,
        String sourceName,
        String targetName,
        boolean dangling) {}
