package com.refinements.visualizer.connection.application.port.out;

/** The workspace revision (the workspace module behind it). */
public interface Revision {

    long current();

    /** Locks and increments; must run inside the caller's transaction. */
    long bump();

    /** Locks; a non-null {@code baseRevision} other than the current one → 409. */
    void check(Long baseRevision);
}
