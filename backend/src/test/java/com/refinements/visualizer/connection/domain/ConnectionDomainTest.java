package com.refinements.visualizer.connection.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.refinements.visualizer.connection.ConnectionKey;
import com.refinements.visualizer.connection.ConnectionKind;
import com.refinements.visualizer.shared.errors.BadRequestException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Connection rules without Spring or a database (specs/005 SC-005). */
class ConnectionDomainTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(60);
    private final UUID orders = UUID.randomUUID();
    private final UUID billing = UUID.randomUUID();

    private Connection connection(String name) {
        Connection c = new Connection(name, ConnectionKind.REQUEST, null, NOW);
        c.setSource(orders, "Orders", NOW);
        c.setTarget(billing, " Billing ", NOW);
        return c;
    }

    @Test
    void keyIsNormalizedSourceTargetAndName() {
        Connection c = connection("Create  Invoice");
        assertThat(c.getTargetName()).isEqualTo("Billing");
        assertThat(c.key()).isEqualTo(ConnectionKey.of("orders", "billing", "create invoice"));
        assertThat(c.isDangling()).isFalse();
    }

    @Test
    void detachKeepsTheNameAndClearsOnlyThatEnd() {
        Connection c = connection("Pay");
        assertThat(c.detach(billing, LATER)).isTrue();
        assertThat(c.getTargetId()).isNull();
        assertThat(c.getTargetName()).isEqualTo("Billing");
        assertThat(c.getSourceId()).isEqualTo(orders);
        assertThat(c.isDangling()).isTrue();
        assertThat(c.view().dangling()).isTrue();
        assertThat(c.getUpdatedAt()).isEqualTo(LATER);
        assertThat(c.detach(UUID.randomUUID(), LATER)).isFalse();
    }

    @Test
    void reattachMatchesDanglingEndsByNormalizedName() {
        Connection c = connection("Pay");
        c.detach(billing, NOW);
        UUID newBilling = UUID.randomUUID();
        assertThat(c.reattach(newBilling, "Other", "other", LATER)).isFalse();
        assertThat(c.reattach(newBilling, "BILLING", "billing", LATER)).isTrue();
        assertThat(c.getTargetId()).isEqualTo(newBilling);
        assertThat(c.getTargetName()).isEqualTo("BILLING");
        assertThat(c.isDangling()).isFalse();
    }

    @Test
    void renamedEndsTakeTheNewName() {
        Connection c = connection("Pay");
        c.endRenamed(orders, "Shop", LATER);
        assertThat(c.getSourceName()).isEqualTo("Shop");
        assertThat(c.getSourceId()).isEqualTo(orders);
        assertThat(c.key().source()).isEqualTo("shop");
        assertThat(c.getTargetName()).isEqualTo("Billing");
    }

    @Test
    void stepsAreAtLeastOneAndAppendAfterTheHighest() {
        assertThat(ConnectionSteps.check(null)).isNull();
        assertThat(ConnectionSteps.check(1)).isEqualTo(1);
        assertThatThrownBy(() -> ConnectionSteps.check(0)).isInstanceOf(BadRequestException.class);

        UUID flow = UUID.randomUUID();
        Connection a = connection("A");
        Connection b = connection("B");
        Connection unstepped = connection("C");
        a.setFlow(flow, NOW);
        a.setStep(2, NOW);
        b.setFlow(flow, NOW);
        b.setStep(5, NOW);
        unstepped.setFlow(flow, NOW);
        assertThat(ConnectionSteps.next(List.of(a, b, unstepped), unstepped.getId()))
                .isEqualTo(6);
        // a connection's own step does not count when it is re-placed
        assertThat(ConnectionSteps.next(List.of(a, b), b.getId())).isEqualTo(3);
        assertThat(ConnectionSteps.next(List.of(), null)).isEqualTo(1);
    }

    @Test
    void endpointsAreNormalizedOrRejected() {
        assertThat(ConnectionRules.checkEndpoint("  ")).isNull();
        assertThat(ConnectionRules.checkEndpoint("post /invoices")).isEqualTo("POST /invoices");
        assertThatThrownBy(() -> ConnectionRules.checkEndpoint("FETCH /x")).isInstanceOf(BadRequestException.class);
    }
}
