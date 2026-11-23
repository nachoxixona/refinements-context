package com.refinements.visualizer.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Canvas shape (React Connection DTOs, research R8) and layout persistence (FR-014, FR-035). */
class LayoutAndCanvasIT extends PostgresIntegrationTest {

    private MvcResult saveLayout(int version, Map<String, Object> positions, List<String> collapsed) throws Exception {
        return mvc.perform(put("/api/layout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("version", version, "positions", positions, "collapsedCards", collapsed))))
                .andReturn();
    }

    @Test
    void emptyCanvas() throws Exception {
        JsonNode c = canvas();
        assertThat(c.get("nodes")).isEmpty();
        assertThat(c.get("edges")).isEmpty();
        assertThat(c.get("danglingConnections")).isEmpty();
        assertThat(c.has("revision")).isTrue();
        assertThat(c.has("layoutVersion")).isTrue();
    }

    @Test
    void positionsAndCollapsedCardsRoundTripAndStaleWritesAreRejected() throws Exception {
        String ms = microservice("Orders");
        String card = cardId("reference-workspace/order-service.yaml");
        String loose = cardId("reference-workspace/payment-service.json");
        link(ms, card, null);
        int version = canvas().get("layoutVersion").asInt();

        MvcResult saved = saveLayout(
                version,
                Map.of("ms:" + ms, Map.of("x", 10.5, "y", -3), "card:" + loose, Map.of("x", 400, "y", 0)),
                List.of());
        assertThat(saved.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(saved).get("version").asInt()).isEqualTo(version + 1);

        JsonNode c = canvas();
        assertThat(node(c, "ms:" + ms).get("position").get("x").asDouble()).isEqualTo(10.5);
        JsonNode docked = node(c, "card:" + card);
        assertThat(docked.get("parentId").asText()).isEqualTo("ms:" + ms);
        assertThat(docked.get("draggable").asBoolean()).isFalse();
        assertThat(docked.get("data").get("linkedMicroserviceId").asText()).isEqualTo(ms);
        assertThat(node(c, "card:" + loose).get("position").get("x").asDouble()).isEqualTo(400);
        assertThat(node(c, "ms:" + ms).get("data").get("cardTitle").asText()).isEqualTo("Order Service");

        assertThat(saveLayout(version, Map.of(), List.of()).getResponse().getStatus())
                .isEqualTo(409);

        saveLayout(version + 1, Map.of("ms:" + ms, Map.of("x", 1, "y", 1)), List.of(card));
        c = canvas();
        JsonNode collapsedCard = node(c, "card:" + card);
        assertThat(collapsedCard.get("hidden").asBoolean())
                .as("collapsed docked card is sent hidden")
                .isTrue();
        assertThat(collapsedCard.get("data").get("endpoints"))
                .as("its endpoints stay available")
                .isNotEmpty();
        assertThat(node(c, "ms:" + ms).get("data").get("cardCollapsed").asBoolean())
                .isTrue();
    }

    @Test
    void unknownKeysAndDockedCardPositionsArePruned() throws Exception {
        String ms = microservice("Orders");
        String card = cardId("reference-workspace/order-service.yaml");
        link(ms, card, null);
        int version = canvas().get("layoutVersion").asInt();
        saveLayout(
                version,
                Map.of(
                        "ms:" + ms,
                        Map.of("x", 1, "y", 1),
                        "card:" + card,
                        Map.of("x", 2, "y", 2),
                        "ghost",
                        Map.of("x", 3, "y", 3),
                        "ms:00000000-0000-0000-0000-000000000000",
                        Map.of("x", 4, "y", 4)),
                List.of("00000000-0000-0000-0000-000000000000"));
        String stored = jdbc.queryForObject(
                "SELECT positions::text || collapsed_cards::text FROM baseline_layout", String.class);
        assertThat(stored)
                .contains("ms:" + ms)
                .doesNotContain("card:" + card)
                .doesNotContain("ghost")
                .doesNotContain("00000000-0000");
    }
}
