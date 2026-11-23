package com.refinements.visualizer.scenario.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.refinements.visualizer.canvas.BaselineIndex;
import com.refinements.visualizer.shared.errors.BadRequestException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Scenario rules against a hand-built baseline index: no Spring, no database (specs/005 SC-005). */
class ScenarioDomainTest {

    private final ScenarioValidator validator = new ScenarioValidator();
    private final UUID orders = UUID.randomUUID();
    private final UUID gone = UUID.randomUUID();
    private final UUID card = UUID.randomUUID();
    private final UUID connection = UUID.randomUUID();

    private BaselineIndex baseline(String cardHash) {
        return new BaselineIndex(
                Map.of(orders, "orders"),
                Map.of(),
                Map.of(card, cardHash),
                Set.of(),
                Map.of(connection, new BaselineIndex.ConnectionRef("orders", "billing", "pay")),
                Set.of(),
                7L);
    }

    private static ScenarioContent content(
            List<ProposedMicroservice> ms, List<ProposedConnection> connections, ScenarioRemovals removals) {
        return new ScenarioContent(ms, null, connections, null, null, removals, null, null, null);
    }

    @Test
    void missingBaselineReferencesAreOrphansNotErrors() {
        ScenarioContent c = content(
                List.of(new ProposedMicroservice("proposed:a", "Audit", null, null, null)),
                List.of(new ProposedConnection("c1", gone.toString(), "proposed:a", "Log", null, null, null, null)),
                new ScenarioRemovals(List.of(connection, UUID.randomUUID()), null, null));
        validator.validate(c, baseline("h1"));

        List<Orphan> orphans = validator.orphans(c, baseline("h1"));
        assertThat(orphans).extracting(Orphan::ref).contains(gone.toString()).hasSize(2);
        assertThat(orphans.get(0).usedBy()).containsExactly("proposedConnections[0]");
    }

    @Test
    void structuralProblemsAreRejected() {
        ScenarioContent badKey = content(List.of(new ProposedMicroservice("a", "Audit", null, null, null)), null, null);
        assertThatThrownBy(() -> validator.validate(badKey, baseline("h1"))).isInstanceOf(BadRequestException.class);
        ScenarioContent clash =
                content(List.of(new ProposedMicroservice("proposed:a", " ORDERS ", null, null, null)), null, null);
        assertThatThrownBy(() -> validator.validate(clash, baseline("h1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void driftListsCardsWhoseSpecChangedSinceSaving() {
        BaselineRef saved = new BaselineRef(Map.of(card, "h1"), 7L);
        assertThat(validator.drift(saved, baseline("h1"))).isEmpty();
        assertThat(validator.drift(saved, baseline("h2"))).containsExactly(card);
    }

    @Test
    void updatingNormalizesTheNameAndKeepsTheVersion() {
        Instant now = Instant.parse("2026-11-23T10:00:00Z");
        Scenario s = Scenario.create(now);
        s.update("  Checkout v2 ", "d", ScenarioContent.empty(), new BaselineRef(null, 7L), now.plusSeconds(5));
        assertThat(s.getName()).isEqualTo("Checkout v2");
        assertThat(s.getNameNormalized()).isEqualTo("checkout v2");
        assertThat(s.getVersion()).isZero();
        assertThat(s.getUpdatedAt()).isEqualTo(now.plusSeconds(5));
    }
}
