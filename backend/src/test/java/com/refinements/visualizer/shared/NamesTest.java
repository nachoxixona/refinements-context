package com.refinements.visualizer.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NamesTest {

    @Test
    void normalizesCaseTrimAndInternalSpaces() {
        assertThat(Names.norm("  Order   Service ")).isEqualTo("order service");
        assertThat(Names.norm("ORDER\tservice")).isEqualTo("order service");
        assertThat(Names.norm(null)).isEmpty();
        assertThat(Names.norm("   ")).isEmpty();
    }

    @Test
    void cleanKeepsCase() {
        assertThat(Names.clean("  Order   Service ")).isEqualTo("Order Service");
    }
}
