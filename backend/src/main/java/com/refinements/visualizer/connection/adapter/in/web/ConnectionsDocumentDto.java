package com.refinements.visualizer.connection.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocument;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** The connections document wire format v1 (research R4). Optional properties are omitted when absent. */
@Schema(name = "ConnectionsDocument")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"formatVersion", "microservices", "flows", "connections"})
public record ConnectionsDocumentDto(
        int formatVersion, List<MicroserviceDoc> microservices, List<FlowDoc> flows, List<ConnectionDoc> connections) {

    /** The REST shape of a document (omits null properties, as the document text does). */
    static ConnectionsDocumentDto from(ConnectionsDocument d) {
        return new ConnectionsDocumentDto(
                d.formatVersion(),
                d.microservices() == null
                        ? null
                        : d.microservices().stream()
                                .map(m -> new MicroserviceDoc(
                                        m.name(), m.description(), m.team(), m.repository(), m.docs()))
                                .toList(),
                d.flows() == null
                        ? null
                        : d.flows().stream()
                                .map(f -> new FlowDoc(f.name(), f.color()))
                                .toList(),
                d.connections().stream()
                        .map(c -> new ConnectionDoc(
                                c.name(),
                                c.source(),
                                c.target(),
                                c.kind(),
                                c.endpoint(),
                                c.flow(),
                                c.step(),
                                c.description()))
                        .toList());
    }

    @JsonPropertyOrder({"name", "color"})
    public record FlowDoc(String name, String color) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"name", "description", "team", "repository", "docs"})
    public record MicroserviceDoc(String name, String description, String team, String repository, String docs) {

        public MicroserviceDoc(String name, String description) {
            this(name, description, null, null, null);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({"name", "source", "target", "kind", "endpoint", "flow", "step", "description"})
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
