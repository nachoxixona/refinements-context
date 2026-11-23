package com.refinements.visualizer.connection.adapter.in.web;

import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentChanges;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Request and response bodies of {@code /api/connections-document} (contracts/openapi.yaml). */
public final class ConnectionsDocumentBodies {

    private ConnectionsDocumentBodies() {}

    @Schema(name = "ConnectionsDocumentSubmit")
    public record Submit(@NotNull String text, Long baseRevision) {}

    @Schema(name = "ConnectionsDocumentExport")
    public record Export(
            ConnectionsDocumentDto document, String text, long revision, int excludedDanglingConnections) {}

    @Schema(name = "ConnectionsDocumentPreview")
    public record Preview(ConnectionsDocumentChanges changes, long revision) {}

    @Schema(name = "ConnectionsDocumentApplied")
    public record Applied(ConnectionsDocumentChanges changes, long revision) {}
}
