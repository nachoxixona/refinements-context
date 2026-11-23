package com.refinements.visualizer.connection;

import java.util.List;

/** What other modules may ask about flows (contracts/module-apis.md §connection). */
public interface FlowApi {

    /** All flows ordered by normalized name. */
    List<FlowView> findAllOrdered();
}
