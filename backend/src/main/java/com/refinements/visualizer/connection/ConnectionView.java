package com.refinements.visualizer.connection;

/**
 * A connection as other modules see it: the published {@link ConnectionDto} plus its stored identity key (normalized
 * source, target and name; research R3), used for ordering and baseline checks.
 */
public record ConnectionView(ConnectionDto connection, ConnectionKey key) {

    static ConnectionView from(Connection f) {
        return new ConnectionView(
                ConnectionDto.from(f),
                new ConnectionKey(f.getSourceNameNormalized(), f.getTargetNameNormalized(), f.getNameNormalized()));
    }
}
