package com.refinements.visualizer.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** spec 004: steps (US1), ownership (US3), notes (US4) and their document / scenario round-trips. */
class MeetingToolsIT extends PostgresIntegrationTest {

    private MvcResult patchConnection(String id, Map<String, Object> patch) throws Exception {
        return mvc.perform(patch("/api/connections/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(patch)))
                .andReturn();
    }

    private JsonNode edge(String connectionId) throws Exception {
        for (JsonNode e : canvas().get("edges")) {
            if (e.get("id").asText().equals("connection:" + connectionId)) {
                return e.get("data");
            }
        }
        return null;
    }

    private Integer step(String connectionId) throws Exception {
        JsonNode s = edge(connectionId).get("step");
        return s == null || s.isNull() ? null : s.asInt();
    }

    @Test
    void stepsAppendWhenJoiningAFlowAndClearWhenLeaving() throws Exception {
        String a = microservice("A");
        String b = microservice("B");
        String c = microservice("C");
        String checkout = flow("Checkout", "BLUE");
        String ab = connection(a, b, "one");
        String bc = connection(b, c, "two");
        String ca = connection(c, a, "three");
        assertThat(step(ab)).as("no flow, no step").isNull();

        patchConnection(ab, Map.of("flowId", checkout));
        patchConnection(bc, Map.of("flowId", checkout));
        assertThat(step(ab)).isEqualTo(1);
        assertThat(step(bc)).isEqualTo(2);

        Map<String, Object> both = new HashMap<>();
        both.put("flowId", checkout);
        both.put("step", 2);
        assertThat(patchConnection(ca, both).getResponse().getStatus()).isEqualTo(200);
        assertThat(step(ca)).as("parallel with 'two'").isEqualTo(2);

        Map<String, Object> leave = new HashMap<>();
        leave.put("flowId", null);
        patchConnection(bc, leave);
        assertThat(step(bc)).isNull();
        assertThat(patchConnection(bc, Map.of("step", 3)).getResponse().getStatus())
                .as("a step needs a flow")
                .isEqualTo(400);
        assertThat(patchConnection(ab, Map.of("step", 0)).getResponse().getStatus())
                .isEqualTo(400);

        mvc.perform(delete("/api/flows/" + checkout)).andReturn();
        assertThat(step(ab)).as("deleting the flow clears its steps").isNull();
        assertThat(edge(ab).get("flowId").isNull()).isTrue();
    }

    @Test
    void reorderingAFlowSetsEveryStepAtOnce() throws Exception {
        String a = microservice("A");
        String b = microservice("B");
        String flow = flow("Checkout", "BLUE");
        String one = connection(a, b, "one");
        String two = connection(b, a, "two");
        patchConnection(one, Map.of("flowId", flow));
        patchConnection(two, Map.of("flowId", flow));

        MvcResult ok = mvc.perform(put("/api/flows/" + flow + "/steps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "steps",
                                List.of(
                                        Map.of("connectionId", two, "step", 1),
                                        Map.of("connectionId", one, "step", 2))))))
                .andReturn();
        assertThat(ok.getResponse().getStatus()).isEqualTo(200);
        assertThat(step(two)).isEqualTo(1);
        assertThat(step(one)).isEqualTo(2);

        MvcResult partial = mvc.perform(put("/api/flows/" + flow + "/steps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("steps", List.of(Map.of("connectionId", two, "step", 1))))))
                .andReturn();
        assertThat(partial.getResponse().getStatus())
                .as("must list every connection")
                .isEqualTo(400);
    }

    @Test
    void documentRoundTripsStepsAndOwnership() throws Exception {
        String doc =
                """
                {
                  "formatVersion": 2,
                  "microservices": [
                    { "name": "Billing", "team": "Payments", "repository": "https://git.example.com/billing", "docs": "https://docs.example.com/billing" },
                    { "name": "Orders" }
                  ],
                  "flows": [ { "name": "Checkout", "color": "blue" } ],
                  "connections": [
                    { "name": "Create invoice", "source": "Orders", "target": "Billing", "flow": "Checkout", "step": 2 },
                    { "name": "Reserve", "source": "Billing", "target": "Orders", "flow": "Checkout", "step": 1 },
                    { "name": "Notify", "source": "Orders", "target": "Billing", "flow": "Checkout" }
                  ]
                }
                """;
        assertThat(applyDocument(doc, null).getResponse().getStatus()).isEqualTo(200);
        JsonNode exported = exportDocument();
        JsonNode billing = exported.get("document").get("microservices").get(0);
        assertThat(billing.get("team").asText()).isEqualTo("Payments");
        assertThat(billing.get("repository").asText()).isEqualTo("https://git.example.com/billing");
        assertThat(billing.get("docs").asText()).isEqualTo("https://docs.example.com/billing");
        Map<String, Integer> steps = new HashMap<>();
        for (JsonNode c : exported.get("document").get("connections")) {
            steps.put(c.get("name").asText(), c.get("step").asInt());
        }
        assertThat(steps)
                .containsEntry("Create invoice", 2)
                .containsEntry("Reserve", 1)
                .containsEntry("Notify", 3);

        JsonNode preview = read(previewDocument(exported.get("text").asText()));
        assertThat(preview.get("changes").get("unchanged").asBoolean())
                .as("export → import is a no-op (SC-003)")
                .isTrue();
        JsonNode ms = node(
                canvas(),
                "ms:"
                        + canvas().get("nodes")
                                .get(0)
                                .get("data")
                                .get("microserviceId")
                                .asText());
        assertThat(ms.get("data").get("team").asText()).isEqualTo("Payments");
    }

    @Test
    void documentRejectsBadStepsAndUrls() throws Exception {
        String doc =
                """
                {
                  "formatVersion": 2,
                  "microservices": [ { "name": "Billing", "repository": "ftp://example.com" } ],
                  "connections": [
                    { "name": "x", "source": "Orders", "target": "Billing", "step": 1 },
                    { "name": "y", "source": "Orders", "target": "Billing", "step": 1.5 }
                  ]
                }
                """;
        JsonNode errors = read(previewDocument(doc)).get("errors");
        List<String> kinds = new java.util.ArrayList<>();
        errors.forEach(e -> kinds.add(e.get("kind").asText()));
        assertThat(kinds).contains("INVALID_URL", "STEP_WITHOUT_FLOW", "INVALID_STEP");
    }

    @Test
    void ownershipIsValidatedAndPatchable() throws Exception {
        String id = microservice("Billing");
        Map<String, Object> patch = new HashMap<>();
        patch.put("team", "  Payments  team ");
        patch.put("repositoryUrl", "https://git.example.com/billing");
        assertThat(patchMicroservice(id, patch).getResponse().getStatus()).isEqualTo(200);
        JsonNode data = node(canvas(), "ms:" + id).get("data");
        assertThat(data.get("team").asText()).isEqualTo("Payments team");
        assertThat(data.get("repositoryUrl").asText()).isEqualTo("https://git.example.com/billing");
        assertThat(data.get("docsUrl").isNull()).isTrue();

        assertThat(patchMicroservice(id, Map.of("docsUrl", "javascript:alert(1)"))
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);
        Map<String, Object> clear = new HashMap<>();
        clear.put("team", null);
        patchMicroservice(id, clear);
        assertThat(node(canvas(), "ms:" + id).get("data").get("team").isNull()).isTrue();
        assertThat(node(canvas(), "ms:" + id).get("data").get("repositoryUrl").asText())
                .as("absent properties stay")
                .isEqualTo("https://git.example.com/billing");

        MvcResult created = mvc.perform(post("/api/microservices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Orders", "team", "Core", "docsUrl", "http://docs.local/orders"))))
                .andReturn();
        assertThat(read(created).get("microservice").get("team").asText()).isEqualTo("Core");
    }

    @Test
    void notesAreSavedAndListedOnTheCanvas() throws Exception {
        MvcResult created = mvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of(
                                "text",
                                "Who owns refunds?",
                                "kind",
                                "QUESTION",
                                "position",
                                Map.of("x", 10, "y", 20)))))
                .andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String id = read(created).get("id").asText();
        mvc.perform(patch("/api/notes/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("position", Map.of("x", 50, "y", 60), "kind", "DECISION"))))
                .andReturn();
        JsonNode notes = canvas().get("notes");
        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).get("text").asText()).isEqualTo("Who owns refunds?");
        assertThat(notes.get(0).get("kind").asText()).isEqualTo("DECISION");
        assertThat(notes.get(0).get("position").get("x").asDouble()).isEqualTo(50.0);

        assertThat(mvc.perform(post("/api/notes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body(Map.of("text", " ", "position", Map.of("x", 0, "y", 0)))))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .isEqualTo(400);
        mvc.perform(delete("/api/notes/" + id)).andReturn();
        assertThat(read(mvc.perform(get("/api/notes")).andReturn())).isEmpty();
    }

    @Test
    void scenariosKeepNotesStepsAndTeams() throws Exception {
        String a = microservice("A");
        String flow = flow("Checkout", "BLUE");
        Map<String, Object> content = Map.of(
                "proposedMicroservices",
                List.of(Map.of("tempKey", "proposed:x", "name", "X", "team", "Growth")),
                "proposedConnections",
                List.of(Map.of(
                        "id",
                        "proposed:c1",
                        "source",
                        a,
                        "target",
                        "proposed:x",
                        "name",
                        "Points",
                        "flowId",
                        flow,
                        "step",
                        4)),
                "notes",
                List.of(Map.of(
                        "tempKey",
                        "proposed-note:1",
                        "text",
                        "Decided: X owns points",
                        "kind",
                        "DECISION",
                        "position",
                        Map.of("x", 1, "y", 2))));
        MvcResult saved = mvc.perform(post("/api/scenarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", "Loyalty", "content", content))))
                .andReturn();
        assertThat(saved.getResponse().getStatus()).isEqualTo(201);
        JsonNode s = read(saved).get("content");
        assertThat(s.get("notes").get(0).get("kind").asText()).isEqualTo("DECISION");
        assertThat(s.get("proposedConnections").get(0).get("step").asInt()).isEqualTo(4);
        assertThat(s.get("proposedMicroservices").get(0).get("team").asText()).isEqualTo("Growth");
        assertThat(canvas().get("notes"))
                .as("draft notes never reach the baseline")
                .isEmpty();
    }
}
