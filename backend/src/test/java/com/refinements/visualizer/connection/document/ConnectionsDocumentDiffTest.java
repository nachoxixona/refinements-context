package com.refinements.visualizer.connection.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.refinements.visualizer.connection.Connection;
import com.refinements.visualizer.connection.ConnectionKind;
import com.refinements.visualizer.microservice.Microservice;
import com.refinements.visualizer.support.TestSpecs;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** research R5: replace semantics, matching by (source, target, name), microservices never removed. */
class ConnectionsDocumentDiffTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private final ConnectionsDocumentReader reader = new ConnectionsDocumentReader(TestSpecs.props());

    private static Microservice ms(String name, String description) {
        return new Microservice(name, description, NOW);
    }

    private static Connection connection(
            Microservice s, Microservice t, String name, ConnectionKind kind, String description) {
        Connection f = new Connection(name, kind, description, NOW);
        f.setSource(s == null ? null : s.getId(), s == null ? "Gone" : s.getName(), NOW);
        f.setTarget(t.getId(), t.getName(), NOW);
        return f;
    }

    private ConnectionsDocumentDiff.Plan diff(String doc, List<Microservice> ms, List<Connection> connections) {
        return ConnectionsDocumentDiff.diff(reader.read(doc), ms, connections);
    }

    @Test
    void addChangeRemoveKeepingIds() {
        Microservice orders = ms("Orders", null);
        Microservice billing = ms("Billing", null);
        Connection keep = connection(orders, billing, "Create invoice", ConnectionKind.REQUEST, null);
        Connection change = connection(orders, billing, "Order placed", ConnectionKind.REQUEST, null);
        Connection remove = connection(billing, orders, "Old", null, null);
        String doc = "{\"formatVersion\":2,\"connections\":["
                + "{\"name\":\"Create invoice\",\"source\":\"orders\",\"target\":\"billing\",\"kind\":\"request\"},"
                + "{\"name\":\"Order placed\",\"source\":\"Orders\",\"target\":\"Billing\",\"kind\":\"event\"},"
                + "{\"name\":\"New\",\"source\":\"Orders\",\"target\":\"Shipping\"}]}";
        ConnectionsDocumentDiff.Plan p = diff(doc, List.of(orders, billing), List.of(keep, change, remove));
        ConnectionsDocumentChanges c = p.changes();
        assertThat(c.connectionsAdded())
                .extracting(ConnectionsDocumentChanges.ConnectionChangeSummary::name)
                .containsExactly("New");
        assertThat(c.connectionsChanged()).singleElement().satisfies(s -> {
            assertThat(s.connectionId()).isEqualTo(change.getId());
            assertThat(s.kindBefore()).isEqualTo("request");
            assertThat(s.kindAfter()).isEqualTo("event");
        });
        assertThat(c.connectionsRemoved())
                .extracting(ConnectionsDocumentChanges.ConnectionChangeSummary::connectionId)
                .containsExactly(remove.getId());
        assertThat(c.microservicesAdded()).containsExactly("Shipping");
        assertThat(c.unchanged()).isFalse();
        assertThat(p.connectionsToUpdate())
                .extracting(u -> u.connection().getId())
                .containsExactly(change.getId());
    }

    @Test
    void danglingConnectionsMatchByNameAndMicroservicesAreNeverRemoved() {
        Microservice billing = ms("Billing", null);
        Connection dangling = connection(null, billing, "Pay", null, null);
        String doc =
                "{\"formatVersion\":2,\"connections\":[{\"name\":\"Pay\",\"source\":\"Gone\",\"target\":\"Billing\"}]}";
        ConnectionsDocumentDiff.Plan p = diff(doc, List.of(billing, ms("Unmentioned", null)), List.of(dangling));
        assertThat(p.changes().connectionsAdded()).isEmpty();
        assertThat(p.changes().connectionsRemoved()).isEmpty();
        assertThat(p.changes().microservicesAdded()).containsExactly("Gone");
        assertThat(p.changes().microservicesChanged()).isEmpty();
    }

    @Test
    void descriptionAbsentNullOrDifferent() {
        Microservice a = ms("A", "Old");
        Microservice b = ms("B", "Kept");
        Microservice c = ms("C", "Cleared");
        String doc = "{\"formatVersion\":2,\"microservices\":["
                + "{\"name\":\"A\",\"description\":\"New\"},{\"name\":\"B\"},{\"name\":\"C\",\"description\":null},"
                + "{\"name\":\"D\",\"description\":\"Fresh\"}],\"connections\":[]}";
        ConnectionsDocumentDiff.Plan p = diff(doc, List.of(a, b, c), List.of());
        assertThat(p.changes().microservicesChanged())
                .extracting(ConnectionsDocumentChanges.MicroserviceChange::name)
                .containsExactly("A", "C");
        assertThat(p.changes().microservicesAdded()).containsExactly("D");
        assertThat(p.createEntries().get("d").description()).isEqualTo("Fresh");
    }

    @Test
    void flowsAreCreatedRecoloredAndFlowChangesDetected() {
        Microservice a = ms("A", null);
        Microservice b = ms("B", null);
        var checkout = new com.refinements.visualizer.connection.Flow(
                "Checkout", com.refinements.visualizer.connection.FlowColor.BLUE, NOW);
        var keep = new com.refinements.visualizer.connection.Flow(
                "Keep", com.refinements.visualizer.connection.FlowColor.SLATE, NOW);
        Connection f = connection(a, b, "Pay", null, null);
        String doc = "{\"formatVersion\":2,\"flows\":[{\"name\":\"Checkout\",\"color\":\"pink\"},"
                + "{\"name\":\"Refunds\",\"color\":\"orange\"}],"
                + "\"connections\":[{\"name\":\"Pay\",\"source\":\"A\",\"target\":\"B\",\"flow\":\"Refunds\"}]}";
        ConnectionsDocumentDiff.Plan p =
                ConnectionsDocumentDiff.diff(reader.read(doc), List.of(a, b), List.of(checkout, keep), List.of(f));
        assertThat(p.changes().flowsAdded())
                .extracting(ConnectionsDocumentChanges.FlowChange::name)
                .containsExactly("Refunds");
        assertThat(p.changes().flowsChanged()).singleElement().satisfies(g -> {
            assertThat(g.colorBefore()).isEqualTo("blue");
            assertThat(g.colorAfter()).isEqualTo("pink");
        });
        assertThat(p.changes().connectionsChanged()).singleElement().satisfies(c -> assertThat(c.flowAfter())
                .isEqualTo("Refunds"));
        assertThat(p.flows()).hasSize(2);
    }

    @Test
    void anEndpointChangeIsAConnectionChange() {
        Microservice a = ms("A", null);
        Microservice b = ms("B", null);
        Connection f = connection(a, b, "Pay", null, null);
        f.setEndpoint("POST /pay", NOW);
        String doc = "{\"formatVersion\":2,\"connections\":[{\"name\":\"Pay\",\"source\":\"A\",\"target\":\"B\","
                + "\"endpoint\":\"POST /payments\"}]}";
        ConnectionsDocumentDiff.Plan p = ConnectionsDocumentDiff.diff(reader.read(doc), List.of(a, b), List.of(f));
        assertThat(p.changes().connectionsChanged()).singleElement().satisfies(c -> {
            assertThat(c.endpointBefore()).isEqualTo("POST /pay");
            assertThat(c.endpointAfter()).isEqualTo("POST /payments");
        });
    }

    @Test
    void identicalDocumentIsUnchanged() {
        Microservice orders = ms("Orders", "Lifecycle");
        Microservice billing = ms("Billing", null);
        List<Connection> connections = new ArrayList<>();
        connections.add(connection(orders, billing, "Create invoice", ConnectionKind.REQUEST, "Monthly"));
        String text = new ConnectionsDocumentWriter()
                .export(List.of(orders, billing), connections)
                .text();
        assertThat(diff(text, List.of(orders, billing), connections).changes().unchanged())
                .isTrue();
    }
}
