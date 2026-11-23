package com.refinements.visualizer.connection.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** The connections document wire format v1 (research R4). Optional properties are omitted when absent. */
@Schema(name = "ConnectionsDocument")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"formatVersion", "microservices", "flows", "connections"})
public record ConnectionsDocumentDto(
        int formatVersion, List<MicroserviceDoc> microservices, List<FlowDoc> flows, List<ConnectionDoc> connections) {

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
