package com.refinements.visualizer.microservice.application.port.out;

/** The workspace revision (the workspace module behind it). */
public interface Revision {

    /** Locks and increments; must run inside the caller's transaction. */
    long bump();
}
