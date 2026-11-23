package com.refinements.visualizer.connection.domain.document;

import com.refinements.visualizer.connection.ConnectionKey;
import com.refinements.visualizer.connection.ConnectionKind;
import com.refinements.visualizer.connection.FlowColor;
import java.util.List;

/** A validated connections document (research R4). Names are cleaned (trimmed, internal spaces collapsed). */
public record ConnectionsDocumentModel(
        List<MicroserviceEntry> microservices, List<FlowEntry> flows, List<ConnectionEntry> connections) {

    /** A listed flow (FR-052). */
    public record FlowEntry(String name, FlowColor color) {}

    /**
     * {@code descriptionPresent} is false when the entry has no {@code description} property; the same for the
     * ownership properties (spec 004 data-model.md §5).
     */
    public record MicroserviceEntry(
            String name,
            String description,
            boolean descriptionPresent,
            String team,
            boolean teamPresent,
            String repository,
            boolean repositoryPresent,
            String docs,
            boolean docsPresent) {

        public MicroserviceEntry(String name, String description, boolean descriptionPresent) {
            this(name, description, descriptionPresent, null, false, null, false, null, false);
        }
    }

    /** {@code flow} is the cleaned name of a listed flow, or null. */
    public record ConnectionEntry(
            String name,
            String source,
            String target,
            ConnectionKind kind,
            String description,
            String flow,
            String endpoint,
            Integer step) {

        public ConnectionEntry(
                String name,
                String source,
                String target,
                ConnectionKind kind,
                String description,
                String flow,
                String endpoint) {
            this(name, source, target, kind, description, flow, endpoint, null);
        }

        public ConnectionKey key() {
            return ConnectionKey.of(source, target, name);
        }
    }
}
