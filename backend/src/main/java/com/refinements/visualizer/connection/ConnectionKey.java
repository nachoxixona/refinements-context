package com.refinements.visualizer.connection;

import com.refinements.visualizer.shared.Names;

/** A connection's identity (research R3): normalized source, target and name. */
public record ConnectionKey(String source, String target, String name) {

    public static ConnectionKey of(String source, String target, String name) {
        return new ConnectionKey(Names.norm(source), Names.norm(target), Names.norm(name));
    }
}
