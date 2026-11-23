package com.refinements.visualizer.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** User Story 3, document side (FR-020 – FR-025, SC-003, SC-004, SC-005, research R5–R7). */
class ConnectionsDocumentIT extends PostgresIntegrationTest {

    @Test
    void previewChangesNothingAndApplyReplacesTheBaseline() throws Exception {
        String orders = microservice("Orders");
        String legacyConnection = connection(orders, microservice("Legacy"), "Old call");

        MvcResult preview = previewDocument(connectionsFixture("shop.json"));
        assertThat(preview.getResponse().getStatus()).isEqualTo(200);
        JsonNode changes = read(preview).get("changes");
        assertThat(changes.get("connectionsAdded")).hasSize(7);
        assertThat(changes.get("connectionsRemoved")).hasSize(1);
        assertThat(changes.get("microservicesAdded")).hasSize(4);
        assertThat(changes.get("microservicesChanged")).hasSize(1);
        assertThat(changes.get("unchanged").asBoolean()).isFalse();
        assertThat(count("connection")).isEqualTo(1);
        assertThat(count("microservice")).isEqualTo(2);

        long revision = read(preview).get("revision").asLong();
        MvcResult applied = applyDocument(connectionsFixture("shop.json"), revision);
        assertThat(applied.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(applied).get("revision").asLong()).isGreaterThan(revision);
        assertThat(count("connection")).isEqualTo(7);
        assertThat(count("microservice"))
                .as("Legacy is kept: documents never remove microservices")
                .isEqualTo(6);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM connection WHERE id = ?",
                        Integer.class,
                        java.util.UUID.fromString(legacyConnection)))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT description FROM microservice WHERE name = 'Orders'", String.class))
                .isEqualTo("Order lifecycle");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM microservice WHERE spec_card_id IS NULL", Integer.class))
                .isEqualTo(6);
        assertThat(canvas().get("edges")).hasSize(7);
    }

    @Test
    void invalidDocumentsAreRejectedAsAWholeWithPositions() throws Exception {
        applyDocument(connectionsFixture("shop.json"), null);
        List<String> before = connectionRows();

        MvcResult bad = applyDocument(connectionsFixture("invalid-mixed.json"), null);
        assertThat(bad.getResponse().getStatus()).isEqualTo(422);
        JsonNode errors = read(bad).get("errors");
        assertThat(errors.size()).isGreaterThanOrEqualTo(8);
        for (JsonNode e : errors) {
            assertThat(e.get("line").asInt()).isPositive();
            assertThat(e.get("message").asText()).isNotBlank();
        }
        assertThat(errors.findValuesAsText("kind"))
                .contains(
                        "MISSING",
                        "SELF_CONNECTION",
                        "INVALID_KIND",
                        "DUPLICATE_CONNECTION",
                        "UNKNOWN_PROPERTY",
                        "WRONG_TYPE",
                        "TOO_LONG",
                        "DUPLICATE_MICROSERVICE");
        assertThat(connectionRows()).isEqualTo(before);

        MvcResult syntax = previewDocument(connectionsFixture("invalid-syntax.json"));
        assertThat(syntax.getResponse().getStatus()).isEqualTo(422);
        JsonNode s = read(syntax).get("errors").get(0);
        assertThat(s.get("kind").asText()).isEqualTo("SYNTAX");
        assertThat(s.get("line").asInt()).isEqualTo(5);
    }

    @Test
    void staleBaseRevisionIsRefused() throws Exception {
        long revision = read(previewDocument(connectionsFixture("shop.json")))
                .get("revision")
                .asLong();
        microservice("Drawn on the canvas meanwhile");
        MvcResult stale = applyDocument(connectionsFixture("shop.json"), revision);
        assertThat(stale.getResponse().getStatus()).isEqualTo(409);
        assertThat(count("connection")).isZero();
    }

    @Test
    void oversizedDocumentIs413() throws Exception {
        String big = "{\"formatVersion\":2,\"connections\":[],\"x\":\"" + "a".repeat(2 * 1024 * 1024 + 10) + "\"}";
        assertThat(previewDocument(big).getResponse().getStatus()).isEqualTo(413);
    }

    @Test
    void exportReimportIsAnExactNoOpAndApplyingTwiceDuplicatesNothing() throws Exception {
        applyDocument(connectionsFixture("shop.json"), null);
        String orders =
                jdbc.queryForObject("SELECT cast(id AS text) FROM microservice WHERE name = 'Orders'", String.class);
        connection(
                orders,
                jdbc.queryForObject("SELECT cast(id AS text) FROM microservice WHERE name = 'Payments'", String.class),
                "Refund issued");
        List<String> before = connectionRows();

        JsonNode export = exportDocument();
        String text = export.get("text").asText();
        assertThat(export.get("excludedDanglingConnections").asInt()).isZero();
        assertThat(text).endsWith("}\n").contains("\"Refund issued\"");
        JsonNode preview = read(previewDocument(text)).get("changes");
        assertThat(preview.get("unchanged").asBoolean()).isTrue();

        MvcResult applied = applyDocument(text, export.get("revision").asLong());
        assertThat(read(applied).get("changes").get("unchanged").asBoolean()).isTrue();
        assertThat(connectionRows()).isEqualTo(before);
        applyDocument(connectionsFixture("shop.json"), null);
        applyDocument(connectionsFixture("shop.json"), null);
        assertThat(count("connection")).isEqualTo(7);
        assertThat(count("microservice")).isEqualTo(5);
    }

    @Test
    void danglingConnectionsAreNotExportedAndReimportListsThemAsRemoved() throws Exception {
        applyDocument(connectionsFixture("shop.json"), null);
        String billing =
                jdbc.queryForObject("SELECT cast(id AS text) FROM microservice WHERE name = 'Billing'", String.class);
        deleteMicroservice(billing);
        JsonNode export = exportDocument();
        assertThat(export.get("excludedDanglingConnections").asInt()).isEqualTo(5);
        JsonNode changes = read(previewDocument(export.get("text").asText())).get("changes");
        assertThat(changes.get("connectionsRemoved")).hasSize(5);

        // the original document recreates Billing, re-attaching the dangling connections instead of duplicating them
        JsonNode reapplied =
                read(applyDocument(connectionsFixture("shop.json"), null)).get("changes");
        assertThat(reapplied.get("connectionsAdded")).isEmpty();
        assertThat(reapplied.get("microservicesAdded").get(0).asText()).isEqualTo("Billing");
        assertThat(canvas().get("danglingConnections")).isEmpty();
        assertThat(canvas().get("edges")).hasSize(7);
    }

    @Test
    void twoHundredConnectionsBetweenOneHundredMicroservicesUnderFiveSeconds() throws Exception {
        String doc = connectionsFixture("large-200.json");
        long start = System.nanoTime();
        MvcResult preview = previewDocument(doc);
        MvcResult applied = applyDocument(doc, read(preview).get("revision").asLong());
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertThat(applied.getResponse().getStatus()).isEqualTo(200);
        assertThat(count("connection")).isEqualTo(200);
        assertThat(count("microservice")).isEqualTo(100);
        assertThat(millis).as("SC-005 validate + preview + apply").isLessThan(5_000);
    }
}
