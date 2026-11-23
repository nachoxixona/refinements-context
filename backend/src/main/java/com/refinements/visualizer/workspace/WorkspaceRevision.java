package com.refinements.visualizer.workspace;

/**
 * The workspace revision (research R7 of 003). Every microservice or connection write calls {@link #bump()} inside
 * its own transaction; the row lock also serializes those writes. The connections document editor sends the
 * revision it opened as {@code baseRevision}, checked by {@link #check(Long)}.
 */
public interface WorkspaceRevision {

    long current();

    /** Locks the row and increments it. Must run inside the caller's transaction. */
    long bump();

    /** Locks the row and throws 409 when {@code baseRevision} is set and stale. Must run inside a transaction. */
    void check(Long baseRevision);
}
