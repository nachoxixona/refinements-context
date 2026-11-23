package com.refinements.visualizer.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** Flows (FR-049 – FR-052, data-model.md §3b). */
class FlowIT extends PostgresIntegrationTest {

    private MvcResult patchJson(String url, Map<String, Object> body) throws Exception {
        return mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(body(body)))
                .andReturn();
    }

    @Test
    void crudUniqueNamesPaletteAndRevision() throws Exception {
        long rev = canvas().get("revision").asLong();
        String checkout = flow("Checkout", "BLUE");
        assertThat(canvas().get("revision").asLong()).isGreaterThan(rev);
        MvcResult dup = mvc.perform(post("/api/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", " checkout ", "color", "PINK"))))
                .andReturn();
        assertThat(dup.getResponse().getStatus()).isEqualTo(409);
        MvcResult green = mvc.perform(post("/api/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Green", "color", "GREEN"))))
                .andReturn();
        assertThat(green.getResponse().getStatus())
                .as("green is reserved for proposals")
                .isEqualTo(400);

        MvcResult recolored = patchJson("/api/flows/" + checkout, Map.of("name", "Check-out", "color", "PURPLE"));
        assertThat(read(recolored).get("flow").get("color").asText()).isEqualTo("PURPLE");
        JsonNode flows = canvas().get("flows");
        assertThat(flows).hasSize(1);
        assertThat(flows.get(0).get("name").asText()).isEqualTo("Check-out");
    }

    @Test
    void connectionsJoinAndLeaveFlowsAndDeletingAFlowUngroupsThem() throws Exception {
        String a = microservice("A");
        String b = microservice("B");
        String g = flow("Checkout", "BLUE");
        Map<String, Object> req = new HashMap<>();
        req.put("sourceId", a);
        req.put("targetId", b);
        req.put("name", "Pay");
        req.put("flowId", g);
        MvcResult created = mvc.perform(post("/api/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(req)))
                .andReturn();
        String connectionId = read(created).get("connection").get("id").asText();
        assertThat(read(created).get("connection").get("flowId").asText()).isEqualTo(g);
        assertThat(canvas().get("edges").get(0).get("data").get("flowId").asText())
                .isEqualTo(g);

        Map<String, Object> ungroup = new HashMap<>();
        ungroup.put("flowId", null);
        assertThat(read(patchJson("/api/connections/" + connectionId, ungroup))
                        .get("connection")
                        .get("flowId")
                        .isNull())
                .isTrue();
        patchJson("/api/connections/" + connectionId, Map.of("flowId", g));
        assertThat(patchJson(
                                "/api/connections/" + connectionId,
                                Map.of("flowId", "00000000-0000-0000-0000-000000000000"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(404);

        List<String> before = connectionRows();
        assertThat(mvc.perform(delete("/api/flows/" + g))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(204);
        assertThat(count("connection")).isEqualTo(before.size());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM connection WHERE flow_id IS NOT NULL", Integer.class))
                .isZero();
    }

    @Test
    void documentRoundTripIncludesFlows() throws Exception {
        MvcResult applied = applyDocument(connectionsFixture("roundtrip/flows.json"), null);
        assertThat(applied.getResponse().getStatus()).isEqualTo(200);
        assertThat(read(applied).get("changes").get("flowsAdded")).hasSize(3);
        assertThat(jdbc.queryForObject(
                        "SELECT g.name FROM connection f JOIN flow g ON g.id = f.flow_id WHERE f.name = 'Refund issued'",
                        String.class))
                .isEqualTo("Refunds");
        JsonNode export = exportDocument();
        assertThat(export.get("document").get("flows")).hasSize(3);
        assertThat(read(previewDocument(export.get("text").asText()))
                        .get("changes")
                        .get("unchanged")
                        .asBoolean())
                .isTrue();

        String recolor = export.get("text").asText().replace("\"color\": \"blue\"", "\"color\": \"cyan\"");
        JsonNode changes = read(previewDocument(recolor)).get("changes");
        assertThat(changes.get("flowsChanged")).hasSize(1);
        MvcResult unknown = previewDocument(
                "{\"formatVersion\":2,\"connections\":[{\"name\":\"x\",\"source\":\"A\",\"target\":\"B\",\"flow\":\"Nope\"}]}");
        assertThat(unknown.getResponse().getStatus()).isEqualTo(422);
        // flows not in a document are kept
        applyDocument("{\"formatVersion\":2,\"connections\":[]}", null);
        assertThat(count("flow")).isEqualTo(3);
    }
}
