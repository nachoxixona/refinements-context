package com.refinements.visualizer.layout.application.port.out;

import com.refinements.visualizer.layout.domain.BaselineLayout;

/** Where the single baseline-layout row is kept. */
public interface LayoutStore {

    BaselineLayout get();

    /**
     * Writes positions and collapsed cards onto the row loaded by {@link #get()} in this transaction, flushes, and
     * returns the new version. Fails with an optimistic-lock error when another save got there first.
     */
    int save(BaselineLayout layout);
}
