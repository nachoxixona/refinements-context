package com.refinements.visualizer.connection.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.refinements.visualizer.connection.Connection;
import com.refinements.visualizer.connection.Names;
import com.refinements.visualizer.connection.document.ConnectionsDocumentModel.ConnectionEntry;
import com.refinements.visualizer.connection.document.ConnectionsDocumentModel.MicroserviceEntry;
import com.refinements.visualizer.microservice.Microservice;
import com.refinements.visualizer.support.TestSpecs;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** research R4: deterministic export; writer → reader → diff is a no-op (SC-003). */
class ConnectionsDocumentWriterTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");
    private final ConnectionsDocumentReader reader = new ConnectionsDocumentReader(TestSpecs.props());
    private final ConnectionsDocumentWriter writer = new ConnectionsDocumentWriter();

    /** Builds the baseline a document describes, as an apply would. */
    private record Baseline(List<Microservice> microservices, List<Connection> connections) {}

    private Baseline baselineOf(ConnectionsDocumentModel m) {
        Map<String, Microservice> byName = new HashMap<>();
        List<Microservice> ms = new ArrayList<>();
        for (MicroserviceEntry e : m.microservices()) {
            Microservice x = new Microservice(e.name(), e.description(), NOW);
            byName.put(Names.norm(e.name()), x);
            ms.add(x);
        }
        List<Connection> connections = new ArrayList<>();
        for (ConnectionEntry e : m.connections()) {
            Microservice s = byName.computeIfAbsent(Names.norm(e.source()), k -> add(ms, e.source()));
            Microservice t = byName.computeIfAbsent(Names.norm(e.target()), k -> add(ms, e.target()));
            Connection f = new Connection(e.name(), e.kind(), e.description(), NOW);
            f.setSource(s.getId(), s.getName(), NOW);
            f.setTarget(t.getId(), t.getName(), NOW);
            connections.add(f);
        }
        return new Baseline(ms, connections);
    }

    private static Microservice add(List<Microservice> ms, String name) {
        Microservice m = new Microservice(name, null, NOW);
        ms.add(m);
        return m;
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
        List<com.refinements.visualizer.connection.Flow> flows = new ArrayList<>();
        Map<String, java.util.UUID> ids = new HashMap<>();
        for (ConnectionsDocumentModel.FlowEntry g : m.flows()) {
            var flow = new com.refinements.visualizer.connection.Flow(g.name(), g.color(), NOW);
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
        Microservice a = new Microservice("A", null, NOW);
        Connection f = new Connection("Lost", null, null, NOW);
        f.setSource(null, "Gone", NOW);
        f.setTarget(a.getId(), "A", NOW);
        ConnectionsDocumentWriter.Export e = writer.export(List.of(a), List.of(f));
        assertThat(e.excludedDanglingConnections()).isEqualTo(1);
        assertThat(e.document().connections()).isEmpty();
        assertThat(e.document().microservices()).hasSize(1);
    }
}
