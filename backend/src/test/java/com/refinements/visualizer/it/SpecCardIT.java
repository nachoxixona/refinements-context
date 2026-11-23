package com.refinements.visualizer.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.refinements.visualizer.support.TestSpecs;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** User Story 1: spec cards (FR-001 – FR-009, SC-007). */
class SpecCardIT extends PostgresIntegrationTest {

    @Test
    void importsByPasteAndByUrlAsUnlinkedCardsWithoutConnections() throws Exception {
        MvcResult paste = importPaste("reference-workspace/order-service.yaml");
        assertThat(paste.getResponse().getStatus()).isEqualTo(201);
        JsonNode card = read(paste);
        assertThat(card.get("title").asText()).isEqualTo("Order Service");
        assertThat(card.get("specFormat").asText()).isEqualTo("OPENAPI_3_0");
        assertThat(card.get("endpointCount").asInt()).isEqualTo(2);
        assertThat(card.get("linkedMicroserviceId").isNull()).isTrue();

        try (TestSpecs.SpecServer server = new TestSpecs.SpecServer()) {
            MvcResult url = importUrl(server.url("reference-workspace/customer-service.json"));
            assertThat(url.getResponse().getStatus()).isEqualTo(201);
            assertThat(read(url).get("sourceUrl").asText()).endsWith("customer-service.json");
            assertThat(read(url).get("specFormat").asText()).isEqualTo("OPENAPI_3_1");
        }
        assertThat(read(mvc.perform(get("/api/cards")).andReturn())).hasSize(2);

        JsonNode c = canvas();
        assertThat(c.get("nodes")).hasSize(2);
        assertThat(c.get("edges")).isEmpty();
        JsonNode node = node(c, "card:" + card.get("id").asText());
        assertThat(node.get("type").asText()).isEqualTo("specCard");
        assertThat(node.get("parentId").isNull()).isTrue();
        assertThat(node.get("data").get("contentHash").asText()).hasSize(64);
        String endpointKey =
                node.get("data").get("endpoints").get(0).get("endpointKey").asText();
        assertThat(endpointKey).startsWith(card.get("id").asText() + ":");
        assertThat(count("connection")).isZero();
    }

    @Test
    void duplicateContentOrUrlIsRefusedWithTheExistingCard() throws Exception {
        String id = cardId("reference-workspace/order-service.yaml");
        MvcResult dup = importPaste("reference-workspace/order-service.yaml");
        assertThat(dup.getResponse().getStatus()).isEqualTo(409);
        assertThat(read(dup).get("conflict").asText()).isEqualTo("DUPLICATE_CARD");
        assertThat(read(dup).get("existingCardId").asText()).isEqualTo(id);
        assertThat(count("spec_card")).isEqualTo(1);

        try (TestSpecs.SpecServer server = new TestSpecs.SpecServer()) {
            String url = server.url("reference-workspace/payment-service.json");
            String first = read(importUrl(url)).get("id").asText();
            server.override("reference-workspace/payment-service.json", TestSpecs.read("oas30/simple.yaml"));
            MvcResult again = importUrl(url);
            assertThat(again.getResponse().getStatus()).isEqualTo(409);
            assertThat(read(again).get("existingCardId").asText()).isEqualTo(first);
        }
    }

    @Test
    void invalidSpecReturns422WithLocatedErrorsAndCreatesNothing() throws Exception {
        MvcResult r = importPaste("invalid/missing-ref.yaml");
        assertThat(r.getResponse().getStatus()).isEqualTo(422);
        JsonNode errors = read(r).get("errors");
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0).get("location").asText()).contains("paths./things.get.responses.200");
        assertThat(count("spec_card")).isZero();
    }

    @Test
    void resyncUpdatesInPlaceKeepsLinkAndPositionAndFailedSyncKeepsLastGoodContent() throws Exception {
        try (TestSpecs.SpecServer server = new TestSpecs.SpecServer()) {
            String url = server.url("oas30/simple.yaml");
            String id = read(importUrl(url)).get("id").asText();
            String ms = microservice("Widgets");
            assertThat(link(ms, id, null).getResponse().getStatus()).isEqualTo(204);
            String connectionA = connection(ms, microservice("Other"), "Uses");
            List<String> connectionsBefore = connectionRows();

            String changed = TestSpecs.read("oas30/simple.yaml")
                    .replace(
                            "paths:",
                            "paths:\n  /extra:\n    get:\n      responses:\n        '204':\n"
                                    + "          description: none\n");
            server.override("oas30/simple.yaml", changed);
            MvcResult sync = mvc.perform(post("/api/cards/" + id + "/sync")).andReturn();
            assertThat(sync.getResponse().getStatus()).isEqualTo(200);
            JsonNode synced = read(sync);
            assertThat(synced.get("id").asText()).isEqualTo(id);
            assertThat(synced.get("endpointCount").asInt()).isEqualTo(4);
            assertThat(synced.get("linkedMicroserviceId").asText()).isEqualTo(ms);

            MvcResult same = mvc.perform(post("/api/cards/" + id + "/sync")).andReturn();
            assertThat(read(same).get("unchanged").asBoolean()).isTrue();

            server.override("oas30/simple.yaml", TestSpecs.read("invalid/missing-ref.yaml"));
            MvcResult failed = mvc.perform(post("/api/cards/" + id + "/sync")).andReturn();
            assertThat(failed.getResponse().getStatus()).isEqualTo(422);
            JsonNode list = read(mvc.perform(get("/api/cards")).andReturn());
            assertThat(list.get(0).get("lastSyncStatus").asText()).isEqualTo("FAILED");
            assertThat(list.get(0).get("endpointCount").asInt()).isEqualTo(4);

            server.status("oas30/simple.yaml", 503);
            assertThat(mvc.perform(post("/api/cards/" + id + "/sync"))
                            .andReturn()
                            .getResponse()
                            .getStatus())
                    .isEqualTo(502);
            assertThat(connectionRows()).isEqualTo(connectionsBefore).anyMatch(r -> r.startsWith(connectionA));
        }
    }

    @Test
    void pastedCardsResyncWithNewContent() throws Exception {
        String id = cardId("oas30/simple.yaml");
        MvcResult noBody = mvc.perform(post("/api/cards/" + id + "/sync")).andReturn();
        assertThat(noBody.getResponse().getStatus()).isEqualTo(400);
        MvcResult r = mvc.perform(post("/api/cards/" + id + "/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("content", TestSpecs.read("oas31/simple.json")))))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(r).get("specFormat").asText()).isEqualTo("OPENAPI_3_1");
    }

    @Test
    void deletingALinkedCardLeavesTheMicroserviceWithoutSpecAndConnectionsUntouched() throws Exception {
        String id = cardId("reference-workspace/order-service.yaml");
        String ms = microservice("Orders");
        link(ms, id, null);
        connection(ms, microservice("Billing"), "Create invoice");
        List<String> before = connectionRows();

        assertThat(mvc.perform(delete("/api/cards/" + id))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(204);
        assertThat(count("spec_card")).isZero();
        JsonNode node = node(canvas(), "ms:" + ms);
        assertThat(node.get("data").get("specCardId").isNull()).isTrue();
        assertThat(connectionRows()).isEqualTo(before);
        assertThat(mvc.perform(delete("/api/cards/" + id))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);
    }

    @Test
    void sizeLimitIsEnforced() throws Exception {
        String content = "openapi: 3.0.3\ninfo:\n  title: Big\n  version: '1'\npaths: {}\n" + "x-padding: '"
                + "a".repeat(10 * 1024 * 1024 + 10) + "'\n";
        MvcResult r = mvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("sourceType", "PASTE", "content", content))))
                .andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(413);
        assertThat(count("spec_card")).isZero();
    }
}
