package com.refinements.visualizer.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.refinements.visualizer.support.TestSpecs;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** User Story 5: scenario content v2 (FR-036 – FR-043, data-model.md §7). */
class ScenarioIT extends PostgresIntegrationTest {

    private String orders;
    private String billing;
    private String linkedCard;
    private String looseCard;
    private String connectionA;
    private String connectionB;

    @BeforeEach
    void baseline() throws Exception {
        orders = microservice("Orders");
        billing = microservice("Billing");
        linkedCard = cardId("reference-workspace/order-service.yaml");
        looseCard = cardId("reference-workspace/payment-service.json");
        link(orders, linkedCard, null);
        connectionA = connection(orders, billing, "Create invoice");
        connectionB = connection(billing, orders, "Invoice paid");
    }

    private Map<String, Object> content() {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put(
                "proposedMicroservices",
                List.of(Map.of(
                        "tempKey",
                        "proposed:loyalty",
                        "name",
                        "Loyalty",
                        "card",
                        Map.of(
                                "title",
                                "Loyalty API",
                                "endpoints",
                                List.of(Map.of("method", "POST", "path", "/points"))))));
        c.put("proposedLinks", List.of(Map.of("microservice", billing, "cardId", looseCard)));
        c.put(
                "proposedConnections",
                List.of(Map.of(
                        "id",
                        "pf1",
                        "source",
                        orders,
                        "target",
                        "proposed:loyalty",
                        "name",
                        "Points earned",
                        "kind",
                        "EVENT")));
        String endpointKey = canvasEndpointKey(linkedCard);
        c.put(
                "addedFields",
                List.of(Map.of(
                        "cardId",
                        linkedCard,
                        "endpointKey",
                        endpointKey,
                        "direction",
                        "OUT",
                        "field",
                        Map.of("name", "shipping.city", "type", "string"))));
        c.put("removals", Map.of("connections", List.of(connectionA), "microservices", List.of(), "links", List.of()));
        c.put("connectionChanges", List.of(Map.of("connectionId", connectionB, "kind", "REQUEST")));
        c.put("positions", Map.of("proposed:loyalty", Map.of("x", 10, "y", 20)));
        return c;
    }

    private String canvasEndpointKey(String cardId) {
        try {
            return node(canvas(), "card:" + cardId)
                    .get("data")
                    .get("endpoints")
                    .get(0)
                    .get("endpointKey")
                    .asText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MvcResult save(String name, Map<String, Object> content) throws Exception {
        return mvc.perform(post("/api/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", name, "content", content))))
                .andReturn();
    }

    private Map<String, Object> baselineSnapshot() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("connections", connectionRows());
        s.put(
                "ms",
                jdbc.queryForList(
                        "SELECT cast(id AS text) || name || coalesce(cast(spec_card_id AS text), '')"
                                + " FROM microservice ORDER BY 1",
                        String.class));
        s.put("cards", jdbc.queryForList("SELECT content_hash FROM spec_card ORDER BY 1", String.class));
        s.put("layout", jdbc.queryForObject("SELECT positions::text || version FROM baseline_layout", String.class));
        s.put("revision", jdbc.queryForObject("SELECT revision FROM workspace_state", Long.class));
        return s;
    }

    @Test
    void crudWithOptimisticLockingNeverTouchesTheBaseline() throws Exception {
        Map<String, Object> before = baselineSnapshot();
        MvcResult created = save("Loyalty v1", content());
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        JsonNode s = read(created);
        assertThat(s.get("orphans")).isEmpty();
        assertThat(s.get("content")
                        .get("proposedConnections")
                        .get(0)
                        .get("kind")
                        .asText())
                .isEqualTo("EVENT");
        assertThat(s.get("content").get("removals").get("connections").get(0).asText())
                .isEqualTo(connectionA);
        String id = s.get("id").asText();

        assertThat(save("loyalty V1 ", content()).getResponse().getStatus()).isEqualTo(409);
        MvcResult updated = mvc.perform(put("/api/scenarios/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Loyalty v2", "content", content(), "version", 0))))
                .andReturn();
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        MvcResult stale = mvc.perform(put("/api/scenarios/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Loyalty v3", "content", content(), "version", 0))))
                .andReturn();
        assertThat(stale.getResponse().getStatus()).isEqualTo(409);
        assertThat(read(mvc.perform(get("/api/scenarios")).andReturn())).hasSize(1);
        assertThat(baselineSnapshot()).isEqualTo(before);
        assertThat(mvc.perform(delete("/api/scenarios/" + id))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(204);
        assertThat(baselineSnapshot()).isEqualTo(before);
    }

    @Test
    void linkAndConnectionRulesAreValidated() throws Exception {
        Map<String, Object> c = content();
        c.put("proposedLinks", List.of(Map.of("microservice", orders, "cardId", looseCard)));
        assertThat(save("A", c).getResponse().getStatus())
                .as("orders already has a card")
                .isEqualTo(400);

        c = content();
        c.put("removals", Map.of("connections", List.of(), "microservices", List.of(), "links", List.of(orders)));
        c.put("proposedLinks", List.of(Map.of("microservice", orders, "cardId", looseCard)));
        assertThat(save("B", c).getResponse().getStatus())
                .as("its link is proposed for removal")
                .isEqualTo(201);

        c = content();
        c.put("proposedLinks", List.of(Map.of("microservice", billing, "cardId", linkedCard)));
        assertThat(save("C", c).getResponse().getStatus())
                .as("card is linked to orders")
                .isEqualTo(400);

        c = content();
        c.put(
                "proposedConnections",
                List.of(Map.of("id", "x", "source", orders, "target", billing, "name", "create invoice")));
        c.put("removals", Map.of("connections", List.of(), "microservices", List.of(), "links", List.of()));
        assertThat(save("D", c).getResponse().getStatus())
                .as("duplicates a baseline connection")
                .isEqualTo(400);

        c.put("removals", Map.of("connections", List.of(connectionA), "microservices", List.of(), "links", List.of()));
        assertThat(save("E", c).getResponse().getStatus())
                .as("the baseline connection is proposed for removal")
                .isEqualTo(201);

        c = content();
        c.put("proposedMicroservices", List.of(Map.of("tempKey", "proposed:x", "name", "orders")));
        c.put("proposedConnections", List.of());
        assertThat(save("F", c).getResponse().getStatus())
                .as("name clash with the baseline")
                .isEqualTo(400);
    }

    @Test
    void orphansAndDriftAreReportedNotDropped() throws Exception {
        String id = read(save("Loyalty", content())).get("id").asText();
        deleteMicroservice(billing);
        mvc.perform(delete("/api/connections/" + connectionB));
        mvc.perform(post("/api/cards/" + linkedCard + "/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("content", TestSpecs.read("oas30/simple.yaml")))));

        JsonNode s = read(mvc.perform(get("/api/scenarios/" + id)).andReturn());
        List<String> usedBy = s.get("orphans").findValuesAsText("usedBy");
        assertThat(s.get("orphans").toString())
                .contains("proposedLinks[0]", "connectionChanges[0]", "addedFields[0]", billing, connectionB);
        assertThat(s.get("baselineDrift").get(0).asText()).isEqualTo(linkedCard);
        assertThat(s.get("content").get("proposedLinks")).hasSize(1);
        assertThat(usedBy).isNotNull();
    }

    @Test
    void proposedConnectionsAndChangesCanUseFlowsAndMissingFlowsAreOrphans() throws Exception {
        String g = flow("Checkout", "BLUE");
        Map<String, Object> c = content();
        c.put(
                "proposedConnections",
                List.of(Map.of(
                        "id", "pf1", "source", orders, "target", "proposed:loyalty", "name", "Points", "flowId", g)));
        c.put("connectionChanges", List.of(Map.of("connectionId", connectionB, "flowId", g)));
        String id = read(save("Grouped", c)).get("id").asText();
        assertThat(read(mvc.perform(get("/api/scenarios/" + id)).andReturn()).get("orphans"))
                .isEmpty();
        mvc.perform(delete("/api/flows/" + g));
        JsonNode s = read(mvc.perform(get("/api/scenarios/" + id)).andReturn());
        assertThat(s.get("orphans").toString())
                .contains(g)
                .contains("proposedConnections[0]")
                .contains("connectionChanges[0]");
        c.put("connectionChanges", List.of(Map.of("connectionId", connectionB, "flowId", "bogus")));
        c.put("proposedConnections", List.of());
        assertThat(save("Bad", c).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void exportAndImport() throws Exception {
        String id = read(save("Loyalty", content())).get("id").asText();
        JsonNode doc = read(mvc.perform(get("/api/scenarios/" + id + "/export")).andReturn());
        assertThat(doc.get("formatVersion").asInt()).isEqualTo(3);

        MvcResult imported = mvc.perform(post("/api/scenarios/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doc.toString()))
                .andReturn();
        assertThat(imported.getResponse().getStatus()).isEqualTo(201);
        JsonNode i = read(imported);
        assertThat(i.get("name").asText()).isEqualTo("Loyalty (imported)");
        assertThat(i.get("content")).isEqualTo(doc.get("content"));

        // older format versions are not accepted
        MvcResult old = mvc.perform(post("/api/scenarios/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formatVersion\":2,\"name\":\"x\",\"content\":{}}"))
                .andReturn();
        assertThat(old.getResponse().getStatus()).isEqualTo(400);

        MvcResult unsupported = mvc.perform(post("/api/scenarios/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formatVersion\":9,\"name\":\"x\",\"content\":{}}"))
                .andReturn();
        assertThat(unsupported.getResponse().getStatus()).isEqualTo(400);
    }
}
