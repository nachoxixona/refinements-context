package com.refinements.visualizer.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** User Story 3, canvas side: connections (FR-017, FR-018a, FR-024, FR-027, data-model.md §3). */
class ConnectionIT extends PostgresIntegrationTest {

    @Test
    void severalNamedConnectionsPerPairInBothDirections() throws Exception {
        String orders = microservice("Orders");
        String billing = microservice("Billing");
        String catalog = microservice("Catalog");
        long rev0 = canvas().get("revision").asLong();

        MvcResult first = createConnection(orders, billing, "Create invoice", "REQUEST");
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(read(first).get("revision").asLong()).isGreaterThan(rev0);
        assertThat(createConnection(orders, billing, "Order placed", "EVENT")
                        .getResponse()
                        .getStatus())
                .isEqualTo(201);
        assertThat(createConnection(billing, orders, "Create invoice", null)
                        .getResponse()
                        .getStatus())
                .as("same name, opposite direction")
                .isEqualTo(201);
        assertThat(createConnection(orders, catalog, "Create invoice", null)
                        .getResponse()
                        .getStatus())
                .as("same name, other pair")
                .isEqualTo(201);

        MvcResult dup = createConnection(orders, billing, " create  INVOICE ", null);
        assertThat(dup.getResponse().getStatus()).isEqualTo(409);
        assertThat(read(dup).get("conflict").asText()).isEqualTo("DUPLICATE_CONNECTION");

        MvcResult self = createConnection(orders, orders, "Loop", null);
        assertThat(self.getResponse().getStatus()).isEqualTo(409);
        assertThat(read(self).get("conflict").asText()).isEqualTo("SELF_CONNECTION");
        assertThat(createConnection(orders, billing, " ", null).getResponse().getStatus())
                .isEqualTo(400);

        JsonNode c = canvas();
        assertThat(c.get("edges")).hasSize(4);
        JsonNode e = c.get("edges").get(0);
        assertThat(e.get("type").asText()).isEqualTo("connection");
        assertThat(e.get("id").asText()).startsWith("connection:");
        assertThat(e.get("source").asText()).startsWith("ms:");
    }

    @Test
    void endpointsAreValidatedAndFlaggedWhenNotInTheTargetsSpec() throws Exception {
        String orders = microservice("Orders");
        String billing = microservice("Billing");
        String noSpec = microservice("NoSpec");
        link(billing, cardId("reference-workspace/order-service.yaml"), null); // POST /orders, GET /orders/{orderId}

        Map<String, Object> req = new HashMap<>();
        req.put("sourceId", orders);
        req.put("targetId", billing);
        req.put("name", "Get order");
        req.put("endpoint", "get /orders/{id}");
        MvcResult created = mvc.perform(post("/api/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(req)))
                .andReturn();
        assertThat(read(created).get("connection").get("endpoint").asText()).isEqualTo("GET /orders/{id}");
        String id = read(created).get("connection").get("id").asText();
        req.put("name", "Bad");
        req.put("endpoint", "FETCH /orders");
        assertThat(mvc.perform(post("/api/connections")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(req)))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);
        req.put("targetId", noSpec);
        req.put("name", "Unknown API");
        req.put("endpoint", "POST /whatever");
        mvc.perform(
                post("/api/connections").contentType(MediaType.APPLICATION_JSON).content(body(req)));

        JsonNode edges = canvas().get("edges");
        java.util.Map<String, Boolean> missing = new java.util.HashMap<>();
        edges.forEach(e -> missing.put(
                e.get("data").get("name").asText(),
                e.get("data").get("endpointMissing").asBoolean()));
        assertThat(missing).containsEntry("Get order", false).containsEntry("Unknown API", false);

        patchConnection(id, Map.of("endpoint", "POST /invoices"));
        JsonNode flagged = canvas().get("edges");
        flagged.forEach(e -> {
            if (e.get("data").get("name").asText().equals("Get order")) {
                assertThat(e.get("data").get("endpointMissing").asBoolean()).isTrue();
            }
        });
        Map<String, Object> clear = new HashMap<>();
        clear.put("endpoint", null);
        assertThat(read(patchConnection(id, clear))
                        .get("connection")
                        .get("endpoint")
                        .isNull())
                .isTrue();
    }

    @Test
    void patchAndDelete() throws Exception {
        String orders = microservice("Orders");
        String billing = microservice("Billing");
        String id = read(createConnection(orders, billing, "Create invoice", "REQUEST"))
                .get("connection")
                .get("id")
                .asText();
        connection(orders, billing, "Order placed");

        MvcResult renamed = patchConnection(id, Map.of("name", "Bill", "description", "Monthly"));
        assertThat(renamed.getResponse().getStatus()).isEqualTo(200);
        JsonNode f = read(renamed).get("connection");
        assertThat(f.get("name").asText()).isEqualTo("Bill");
        assertThat(f.get("kind").asText()).as("absent kind is unchanged").isEqualTo("REQUEST");

        Map<String, Object> noKind = new HashMap<>();
        noKind.put("kind", null);
        assertThat(read(patchConnection(id, noKind))
                        .get("connection")
                        .get("kind")
                        .isNull())
                .isTrue();
        assertThat(patchConnection(id, Map.of("kind", "ASYNC")).getResponse().getStatus())
                .isEqualTo(400);
        assertThat(patchConnection(id, Map.of("name", "order placed"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(409);

        assertThat(mvc.perform(delete("/api/connections/" + id))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(204);
        assertThat(count("connection")).isEqualTo(1);
    }

    @Test
    void deletedMicroserviceLeavesDanglingConnectionsThatRecreateOrReassignResolve() throws Exception {
        String orders = microservice("Orders");
        String billing = microservice("Billing");
        String a = connection(orders, billing, "Create invoice");
        String b = connection(billing, orders, "Invoice paid");
        String c = connection(orders, billing, "Refund");

        deleteMicroservice(billing);
        JsonNode canvas = canvas();
        assertThat(canvas.get("edges")).isEmpty();
        assertThat(canvas.get("danglingConnections")).hasSize(3);
        JsonNode d = canvas.get("danglingConnections").get(0);
        assertThat(d.get("dangling").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM connection WHERE target_id IS NULL AND target_name = 'Billing'",
                        Integer.class))
                .isEqualTo(2);

        MvcResult recreated = createMicroservice("billing");
        assertThat(read(recreated).get("reattachedConnectionIds")).hasSize(3);
        assertThat(canvas().get("edges")).hasSize(3);
        String newBilling = read(recreated).get("microservice").get("id").asText();

        deleteMicroservice(newBilling);
        String payments = microservice("Payments");
        MvcResult reassigned = mvc.perform(post("/api/connections/" + a + "/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("targetId", payments))))
                .andReturn();
        assertThat(reassigned.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(reassigned).get("connection").get("targetName").asText())
                .isEqualTo("Payments");
        assertThat(read(reassigned).get("connection").get("dangling").asBoolean())
                .isFalse();

        MvcResult selfReassign = mvc.perform(post("/api/connections/" + b + "/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("sourceId", orders))))
                .andReturn();
        assertThat(selfReassign.getResponse().getStatus()).isEqualTo(409);
        mvc.perform(delete("/api/connections/" + c));
        assertThat(canvas().get("danglingConnections")).hasSize(1);
    }

    private MvcResult patchConnection(String id, Map<String, Object> body) throws Exception {
        return mvc.perform(patch("/api/connections/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(body)))
                .andReturn();
    }
}
