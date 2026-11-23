package com.refinements.visualizer.connection.adapter.out.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.refinements.visualizer.connection.domain.Connection;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentDiff;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentModel;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentModel.ConnectionEntry;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentModel.MicroserviceEntry;
import com.refinements.visualizer.microservice.MicroserviceView;
import com.refinements.visualizer.shared.Names;
import com.refinements.visualizer.support.TestSpecs;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** research R4: deterministic export; writer → reader → diff is a no-op (SC-003). */
class ConnectionsDocumentWriterTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private final ConnectionsDocumentReader reader = new ConnectionsDocumentReader(TestSpecs.props());
    private final ConnectionsDocumentWriter writer = new ConnectionsDocumentWriter();

    /** Builds the baseline a document describes, as an apply would. */
    private record Baseline(List<MicroserviceView> microservices, List<Connection> connections) {}

    private Baseline baselineOf(ConnectionsDocumentModel m) {
        Map<String, MicroserviceView> byName = new HashMap<>();
        List<MicroserviceView> ms = new ArrayList<>();
        for (MicroserviceEntry e : m.microservices()) {
            MicroserviceView x = view(e.name(), e.description());
            byName.put(Names.norm(e.name()), x);
            ms.add(x);
        }
        List<Connection> connections = new ArrayList<>();
        for (ConnectionEntry e : m.connections()) {
            MicroserviceView s = byName.computeIfAbsent(Names.norm(e.source()), k -> add(ms, e.source()));
            MicroserviceView t = byName.computeIfAbsent(Names.norm(e.target()), k -> add(ms, e.target()));
            Connection f = new Connection(e.name(), e.kind(), e.description(), NOW);
            f.setSource(s.id(), s.name(), NOW);
            f.setTarget(t.id(), t.name(), NOW);
            connections.add(f);
        }
        return new Baseline(ms, connections);
    }

    private static MicroserviceView add(List<MicroserviceView> ms, String name) {
        MicroserviceView m = view(name, null);
        ms.add(m);
        return m;
    }

    private static MicroserviceView view(String name, String description) {
        return new MicroserviceView(UUID.randomUUID(), name, Names.norm(name), description, null, null, null, null);
    }

    @Test
    void roundTripsEveryFixtureAsANoOp() {
        for (String f : List.of("roundtrip/minimal.json", "roundtrip/descriptions.json", "roundtrip/shop.json")) {
            // flows are covered by roundTripsFlows below
            Baseline b = baselineOf(reader.read(ConnectionsDocumentValidatorTest.fixture(f)));
            String text = writer.export(b.microservices(), b.connections()).text();
            ConnectionsDocumentModel again = reader.read(text);
            assertThat(ConnectionsDocumentDiff.diff(again, b.microservices(), b.connections())
                            .changes()
                            .unchanged())
                    .as(f)
                    .isTrue();
            assertThat(writer.export(b.microservices(), b.connections()).text())
                    .as("deterministic")
                    .isEqualTo(text);
        }
    }

    @Test
    void roundTripsFlows() {
        ConnectionsDocumentModel m = reader.read(ConnectionsDocumentValidatorTest.fixture("roundtrip/flows.json"));
        List<com.refinements.visualizer.connection.domain.Flow> flows = new ArrayList<>();
        Map<String, java.util.UUID> ids = new HashMap<>();
        for (ConnectionsDocumentModel.FlowEntry g : m.flows()) {
            var flow = new com.refinements.visualizer.connection.domain.Flow(g.name(), g.color(), NOW);
            flows.add(flow);
            ids.put(Names.norm(g.name()), flow.getId());
        }
        Baseline b = baselineOf(m);
        for (int i = 0; i < m.connections().size(); i++) {
            String g = m.connections().get(i).flow();
            if (g != null) {
                b.connections().get(i).setFlow(ids.get(Names.norm(g)), NOW);
            }
            b.connections().get(i).setEndpoint(m.connections().get(i).endpoint(), NOW);
        }
        String text = writer.export(b.microservices(), flows, b.connections()).text();
        assertThat(text)
                .contains("\"flow\": \"Refunds\"")
                .contains("\"color\": \"slate\"")
                .contains("\"endpoint\": \"POST /invoices/{id}\"");
        ConnectionsDocumentModel again = reader.read(text);
        assertThat(ConnectionsDocumentDiff.diff(again, b.microservices(), flows, b.connections())
                        .changes()
                        .unchanged())
                .isTrue();
    }

    @Test
    void sortsOmitsAbsentPropertiesAndEndsWithANewline() {
        Baseline b = baselineOf(reader.read(ConnectionsDocumentValidatorTest.fixture("roundtrip/descriptions.json")));
        String text = writer.export(b.microservices(), b.connections()).text();
        assertThat(text).startsWith("{\n  \"formatVersion\": 2,\n  \"microservices\": [\n    {\n");
        assertThat(text).endsWith("}\n");
        assertThat(text.indexOf("\"Alpha\"")).isLessThan(text.indexOf("\"Beta\""));
        assertThat(text).contains("\"Lonely\"").doesNotContain("null").doesNotContain("\t");
        int pingAB = text.indexOf("\"Health\"");
        int pongBA = text.indexOf("\"Pong\"");
        assertThat(pingAB).isLessThan(pongBA);
        assertThat(writer.export(List.of(), List.of()).text())
                .isEqualTo("{\n  \"formatVersion\": 2,\n  \"connections\": []\n}\n");
    }

    @Test
    void danglingConnectionsAreExcludedAndCounted() {
        MicroserviceView a = view("A", null);
        Connection f = new Connection("Lost", null, null, NOW);
        f.setSource(null, "Gone", NOW);
        f.setTarget(a.id(), "A", NOW);
        ConnectionsDocumentWriter.Export e = writer.export(List.of(a), List.of(f));
        assertThat(e.excludedDanglingConnections()).isEqualTo(1);
        assertThat(e.document().connections()).isEmpty();
        assertThat(e.document().microservices()).hasSize(1);
    }
}
