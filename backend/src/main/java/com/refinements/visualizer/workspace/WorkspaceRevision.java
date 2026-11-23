package com.refinements.visualizer.workspace;

import com.refinements.visualizer.common.errors.StaleVersionException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The workspace revision (research R7). Every microservice or connection write calls {@link #bump()} inside its
 * own transaction; the row lock also serializes those writes. The connections document editor sends the revision
 * it opened as {@code baseRevision}, checked by {@link #check(Long)}.
 */
@Service
public class WorkspaceRevision {

    private final WorkspaceStateRepository repository;

    public WorkspaceRevision(WorkspaceStateRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public long current() {
        return repository
                .findById(WorkspaceState.SINGLETON_ID)
                .map(WorkspaceState::getRevision)
                .orElse(0L);
    }

    /** Locks the row and increments it. Must run inside the caller's transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long bump() {
        return locked().increment();
    }

    /** Locks the row and throws 409 when {@code baseRevision} is set and stale. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void check(Long baseRevision) {
        WorkspaceState state = locked();
        if (baseRevision != null && baseRevision != state.getRevision()) {
            throw new StaleVersionException(
                    "The connections changed since the document was opened (for example on the canvas or in another tab)."
                            + " Reload the document and try again.");
        }
    }

    private WorkspaceState locked() {
        return repository
                .lockSingleton()
                .orElseThrow(() -> new IllegalStateException("workspace_state row 1 is missing"));
    }
}
