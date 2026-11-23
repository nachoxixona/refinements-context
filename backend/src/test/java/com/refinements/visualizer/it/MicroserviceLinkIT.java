package com.refinements.visualizer.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** User Story 2: microservices and 1:1 spec links (FR-010 – FR-016, data-model.md §2). */
class MicroserviceLinkIT extends PostgresIntegrationTest {

    @Test
    void createRenameDescribeAndNormalizedUniqueness() throws Exception {
        MvcResult created = createMicroservice("  Orders  ");
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String id = read(created).get("microservice").get("id").asText();
        assertThat(read(created).get("microservice").get("name").asText()).isEqualTo("Orders");
        assertThat(read(created).get("microservice").get("specCardId").isNull()).isTrue();

        MvcResult dup = createMicroservice("orders ");
        assertThat(dup.getResponse().getStatus()).isEqualTo(409);
        assertThat(read(dup).get("conflict").asText()).isEqualTo("DUPLICATE_NAME");

        microservice("Billing");
        MvcResult clash = patchMicroservice(id, Map.of("name", "BILLING"));
        assertThat(clash.getResponse().getStatus()).isEqualTo(409);

        MvcResult renamed = patchMicroservice(id, Map.of("name", "Order  Service", "description", "Lifecycle"));
        assertThat(renamed.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(renamed).get("microservice").get("name").asText()).isEqualTo("Order Service");
        assertThat(read(renamed).get("microservice").get("description").asText())
                .isEqualTo("Lifecycle");

        Map<String, Object> clear = new HashMap<>();
        clear.put("description", null);
        MvcResult cleared = patchMicroservice(id, clear);
        assertThat(read(cleared).get("microservice").get("description").isNull())
                .isTrue();
        assertThat(read(cleared).get("microservice").get("name").asText()).isEqualTo("Order Service");

        assertThat(patchMicroservice(id, Map.of("nickname", "x")).getResponse().getStatus())
                .isEqualTo(400);
        assertThat(createMicroservice("   ").getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    void linkingIsOneToOneWithExplicitReplace() throws Exception {
        String orders = microservice("Orders");
        String billing = microservice("Billing");
        String a = cardId("reference-workspace/order-service.yaml");
        String b = cardId("reference-workspace/payment-service.json");

        assertThat(link(orders, a, null).getResponse().getStatus()).isEqualTo(204);
        MvcResult has = link(orders, b, null);
        assertThat(has.getResponse().getStatus()).isEqualTo(409);
        assertThat(read(has).get("conflict").asText()).isEqualTo("MICROSERVICE_HAS_CARD");

        MvcResult taken = link(billing, a, true);
        assertThat(taken.getResponse().getStatus()).isEqualTo(409);
        assertThat(read(taken).get("conflict").asText()).isEqualTo("CARD_ALREADY_LINKED");

        assertThat(link(orders, b, true).getResponse().getStatus()).isEqualTo(204);
        JsonNode c = canvas();
        assertThat(node(c, "card:" + b).get("parentId").asText()).isEqualTo("ms:" + orders);
        assertThat(node(c, "card:" + a).get("parentId").isNull())
                .as("replaced card is unlinked")
                .isTrue();

        assertThat(unlink(orders).getResponse().getStatus()).isEqualTo(204);
        c = canvas();
        assertThat(node(c, "card:" + b).get("parentId").isNull()).isTrue();
        assertThat(node(c, "ms:" + orders).get("data").get("specCardId").isNull())
                .isTrue();
        assertThat(count("spec_card")).isEqualTo(2);
    }

    @Test
    void connectionsSurviveUnlinkingAndReplacingACard() throws Exception {
        String orders = microservice("Orders");
        String billing = microservice("Billing");
        String a = cardId("reference-workspace/order-service.yaml");
        String b = cardId("reference-workspace/payment-service.json");
        link(orders, a, null);
        connection(orders, billing, "Create invoice");
        connection(billing, orders, "Invoice paid");
        List<String> before = connectionRows();

        unlink(orders);
        assertThat(connectionRows()).isEqualTo(before);
        link(orders, a, null);
        link(orders, b, true);
        assertThat(connectionRows()).isEqualTo(before);
    }

    @Test
    void renameCascadesToConnectionsAndDeleteLeavesCardUnlinked() throws Exception {
        String orders = microservice("Orders");
        String billing = microservice("Billing");
        String card = cardId("reference-workspace/order-service.yaml");
        link(orders, card, null);
        connection(orders, billing, "Create invoice");

        patchMicroservice(orders, Map.of("name", "Sales"));
        assertThat(jdbc.queryForObject("SELECT source_name FROM connection", String.class))
                .isEqualTo("Sales");
        String text = exportDocument().get("text").asText();
        assertThat(text).contains("\"source\": \"Sales\"").doesNotContain("\"Orders\"");

        JsonNode deleted = read(deleteMicroservice(orders));
        assertThat(deleted.get("danglingConnectionIds")).hasSize(1);
        JsonNode c = canvas();
        assertThat(node(c, "card:" + card).get("parentId").isNull()).isTrue();
        assertThat(c.get("danglingConnections")).hasSize(1);
        assertThat(c.get("edges")).isEmpty();
    }
}
