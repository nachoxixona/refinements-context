package com.refinements.visualizer.connection;

import java.util.List;

/** What other modules may ask about connections (contracts/module-apis.md §connection). */
public interface ConnectionApi {

    /** Every connection, attached or dangling, in no particular order. */
    List<KeyedConnection> findAll();
}
