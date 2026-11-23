package com.refinements.visualizer.connection.document;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/** What applying a document changes (FR-020, contracts/openapi.yaml ConnectionsDocumentChanges). */
@Schema(name = "ConnectionsDocumentChanges")
public record ConnectionsDocumentChanges(
        List<String> microservicesAdded,
        List<MicroserviceChange> microservicesChanged,
        List<ConnectionChangeSummary> connectionsAdded,
        List<ConnectionChangeSummary> connectionsChanged,
        List<ConnectionChangeSummary> connectionsRemoved,
        List<FlowChange> flowsAdded,
        List<FlowChange> flowsChanged,
        boolean unchanged) {

    @Schema(name = "FlowChange")
    public record FlowChange(String name, String colorBefore, String colorAfter) {}

    /** Before/after of each listed property; unchanged properties have equal values (spec 004 FR-016). */
    public record MicroserviceChange(
            String name,
            String descriptionBefore,
            String descriptionAfter,
            String teamBefore,
            String teamAfter,
            String repositoryBefore,
            String repositoryAfter,
            String docsBefore,
            String docsAfter) {}

    @Schema(name = "ConnectionChangeSummary")
    public record ConnectionChangeSummary(
            UUID connectionId,
            String name,
            String source,
            String target,
            String kindBefore,
            String kindAfter,
            String descriptionBefore,
            String descriptionAfter,
            String flowBefore,
            String flowAfter,
            String endpointBefore,
            String endpointAfter,
            Integer stepBefore,
            Integer stepAfter) {}
}
