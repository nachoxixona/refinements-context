package com.refinements.visualizer.layout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.refinements.visualizer.shared.XYPosition;
import com.refinements.visualizer.shared.errors.StaleVersionException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Layout rules with no Spring and no database. */
class BaselineLayoutDomainTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");

    @Test
    void replacingPrunesUnknownKeysAndCards() {
        UUID card = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        BaselineLayout l = new BaselineLayout(Map.of(), List.of(), 3, NOW);
        l.replace(
                Map.of("ms:a", new XYPosition(1.0, 1.0), "ms:deleted", new XYPosition(2.0, 2.0)),
                List.of(card, gone),
                Set.of("ms:a"),
                Set.of(card),
                NOW);
        assertThat(l.getPositions()).containsOnlyKeys("ms:a");
        assertThat(l.getCollapsedCards()).containsExactly(card);
    }

    @Test
    void anOlderVersionIsRejected() {
        BaselineLayout l = new BaselineLayout(Map.of(), List.of(), 3, NOW);
        l.checkVersion(3);
        assertThatThrownBy(() -> l.checkVersion(2)).isInstanceOf(StaleVersionException.class);
    }
}
