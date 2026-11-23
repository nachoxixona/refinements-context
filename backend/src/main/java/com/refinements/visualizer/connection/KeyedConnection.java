package com.refinements.visualizer.connection;

/**
 * A connection plus its stored identity key (normalized source, target and name; research R3), used for ordering
 * and baseline checks.
 */
public record KeyedConnection(ConnectionView connection, ConnectionKey key) {}
