package com.refinements.visualizer.workspace.domain;

import com.refinements.visualizer.shared.errors.StaleVersionException;

/** data-model.md §6 WorkspaceState.revision (research R7 of 003). Plain Java. */
public class WorkspaceState {

    private long revision;

    public WorkspaceState(long revision) {
        this.revision = revision;
    }

    public long getRevision() {
        return revision;
    }

    /** Increments and returns the new revision. */
    public long increment() {
        return ++revision;
    }

    /** Throws 409 when {@code baseRevision} is set and differs from the current revision. */
    public void checkFresh(Long baseRevision) {
        if (baseRevision != null && baseRevision != revision) {
            throw new StaleVersionException(
                    "The connections changed since the document was opened (for example on the canvas or in another tab)."
                            + " Reload the document and try again.");
        }
    }
}
