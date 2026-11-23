package com.refinements.visualizer.connection.application.port.in;

import com.refinements.visualizer.connection.FlowColor;
import com.refinements.visualizer.connection.FlowView;
import java.util.List;
import java.util.UUID;

/** Flows: create, rename, recolor, reorder, delete (FR-049, FR-050, spec 004 FR-004). Every write bumps the revision. */
public interface FlowUseCases {

    /** A partial change: null properties are left as they are. */
    record FlowChanges(String name, FlowColor color) {}

    record StepAssignment(UUID connectionId, Integer step) {}

    /** The flow after a write and the new workspace revision. */
    record Mutation(FlowView flow, long revision) {}

    Mutation create(String name, FlowColor color);

    Mutation update(UUID id, FlowChanges changes);

    /**
     * Sets the steps of all the flow's connections at once: every connection of the flow exactly once, each with a
     * step ≥ 1.
     *
     * @return the new workspace revision
     */
    long setSteps(UUID id, List<StepAssignment> steps);

    /** Deletes the flow; its connections are ungrouped and lose their steps. */
    void delete(UUID id);
}
