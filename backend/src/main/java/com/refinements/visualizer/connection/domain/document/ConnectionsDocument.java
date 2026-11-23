package com.refinements.visualizer.connection.domain.document;

import java.util.List;

/**
 * The connections document wire format as plain data (research R4): what the export writes, property by
 * property. Absent optional properties are null; {@code microservices} and {@code flows} are null when empty.
 */
public record ConnectionsDocument(
        int formatVersion, List<MicroserviceDoc> microservices, List<FlowDoc> flows, List<ConnectionDoc> connections) {

    /** The document format this backend reads and writes. */
    public static final int FORMAT_VERSION = 2;

    public record FlowDoc(String name, String color) {}

    public record MicroserviceDoc(String name, String description, String team, String repository, String docs) {}

    public record ConnectionDoc(
            String name,
            String source,
            String target,
            String kind,
            String endpoint,
            String flow,
            Integer step,
            String description) {}
}
