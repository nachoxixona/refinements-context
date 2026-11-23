package com.refinements.visualizer.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.refinements.visualizer.shared.errors.StaleVersionException;
import org.junit.jupiter.api.Test;

/** Revision rules with no Spring and no database. */
class WorkspaceStateDomainTest {

    @Test
    void incrementsAndChecksFreshness() {
        WorkspaceState s = new WorkspaceState(4);
        assertThat(s.increment()).isEqualTo(5);
        s.checkFresh(null);
        s.checkFresh(5L);
        assertThatThrownBy(() -> s.checkFresh(4L)).isInstanceOf(StaleVersionException.class);
    }
}
