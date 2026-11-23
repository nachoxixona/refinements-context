package com.refinements.visualizer.connection.domain;

import com.refinements.visualizer.shared.errors.BadRequestException;
import java.util.Collection;
import java.util.UUID;

/**
 * Step rules of spec 004 (data-model.md §1 Connection.step, FR-001, FR-002): a step only exists with a flow, is a
 * whole number ≥ 1, and a connection joining a flow without a step is appended after the flow's highest step.
 */
public final class ConnectionSteps {

    private ConnectionSteps() {}

    /** @throws BadRequestException when {@code step} is not null and not ≥ 1 */
    public static Integer check(Integer step) {
        if (step != null && step < 1) {
            throw new BadRequestException("step must be a whole number of at least 1.");
        }
        return step;
    }

    /** The step after the highest one among {@code flowMembers}, ignoring {@code except}'s own step. */
    public static int next(Collection<Connection> flowMembers, UUID except) {
        int max = 0;
        for (Connection c : flowMembers) {
            if (!c.getId().equals(except) && c.getStep() != null) {
                max = Math.max(max, c.getStep());
            }
        }
        return max + 1;
    }
}
