package com.refinements.visualizer.connection.application.port.in;

import com.refinements.visualizer.connection.domain.document.ConnectionsDocument;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentChanges;

/** Export, preview and replace-apply of the connections document (FR-020 – FR-025, research R4, R5, R7). */
public interface ConnectionsDocumentUseCases {

    record Exported(ConnectionsDocument document, String text, long revision, int excludedDanglingConnections) {}

    /** What a document changes (or changed) and the workspace revision. */
    record Outcome(ConnectionsDocumentChanges changes, long revision) {}

    Exported export();

    Outcome preview(String text);

    /** Validates, re-diffs and applies in one transaction; a stale {@code baseRevision} → 409 (research R5). */
    Outcome apply(String text, Long baseRevision);
}
