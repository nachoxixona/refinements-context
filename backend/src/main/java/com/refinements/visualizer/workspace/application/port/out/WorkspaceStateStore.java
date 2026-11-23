package com.refinements.visualizer.workspace.application.port.out;

import com.refinements.visualizer.workspace.domain.WorkspaceState;

/** Where the single workspace-state row is kept. */
public interface WorkspaceStateStore {

    /** The current revision, 0 when the row is missing. */
    long currentRevision();

    /** Loads the row with a pessimistic write lock held until the transaction ends. */
    WorkspaceState lock();

    /** Writes the state back onto the locked row (flushed at commit). */
    void save(WorkspaceState state);
}
