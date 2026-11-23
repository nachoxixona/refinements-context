package com.refinements.visualizer.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refinements.visualizer.support.TestSpecs;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Integration tests against PostgreSQL 16 in Testcontainers, so the production dialect and Flyway schema
 * are always exercised. One container is shared by all IT classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM saved_scenario");
        jdbc.update("DELETE FROM note");
        jdbc.update("DELETE FROM connection");
        jdbc.update("DELETE FROM flow");
        jdbc.update("DELETE FROM microservice");
        jdbc.update("DELETE FROM spec_card");
        jdbc.update("UPDATE baseline_layout SET positions = '{}'::jsonb, collapsed_cards = '[]'::jsonb");
    }

    protected String body(Object value) throws Exception {
        return json.writeValueAsString(value);
    }

    protected JsonNode read(MvcResult result) throws Exception {
        String s = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return s.isEmpty() ? json.nullNode() : json.readTree(s);
    }

    protected int count(String table) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }

    protected static String connectionsFixture(String name) {
        try (var in = PostgresIntegrationTest.class.getResourceAsStream("/connections/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("No connections fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------- cards ----------

    protected MvcResult importPaste(String fixture) throws Exception {
        return mvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("sourceType", "PASTE", "content", TestSpecs.read(fixture)))))
                .andReturn();
    }

    protected MvcResult importUrl(String url) throws Exception {
        return mvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("sourceType", "URL", "url", url))))
                .andReturn();
    }

    protected String cardId(String fixture) throws Exception {
        MvcResult r = importPaste(fixture);
        if (r.getResponse().getStatus() != 201) {
            throw new AssertionError(fixture + " -> " + r.getResponse().getContentAsString());
        }
        return read(r).get("id").asText();
    }

    // ---------- microservices ----------

    protected MvcResult createMicroservice(String name) throws Exception {
        return mvc.perform(post("/api/microservices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", name))))
                .andReturn();
    }

    protected String microservice(String name) throws Exception {
        MvcResult r = createMicroservice(name);
        if (r.getResponse().getStatus() != 201) {
            throw new AssertionError(name + " -> " + r.getResponse().getContentAsString());
        }
        return read(r).get("microservice").get("id").asText();
    }

    protected MvcResult patchMicroservice(String id, Map<String, Object> patch) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/microservices/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(patch)))
                .andReturn();
    }

    protected MvcResult deleteMicroservice(String id) throws Exception {
        return mvc.perform(delete("/api/microservices/" + id)).andReturn();
    }

    protected MvcResult link(String microserviceId, String cardId, Boolean replace) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("cardId", cardId);
        if (replace != null) {
            req.put("replace", replace);
        }
        return mvc.perform(put("/api/microservices/" + microserviceId + "/card")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(req)))
                .andReturn();
    }

    protected MvcResult unlink(String microserviceId) throws Exception {
        return mvc.perform(delete("/api/microservices/" + microserviceId + "/card"))
                .andReturn();
    }

    // ---------- connections ----------

    protected MvcResult createConnection(String sourceId, String targetId, String name, String kind) throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("sourceId", sourceId);
        req.put("targetId", targetId);
        req.put("name", name);
        req.put("kind", kind);
        return mvc.perform(post("/api/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(req)))
                .andReturn();
    }

    protected String connection(String sourceId, String targetId, String name) throws Exception {
        MvcResult r = createConnection(sourceId, targetId, name, null);
        if (r.getResponse().getStatus() != 201) {
            throw new AssertionError(name + " -> " + r.getResponse().getContentAsString());
        }
        return read(r).get("connection").get("id").asText();
    }

    /** Every connection row as "source|target|name|kind|sourceId|targetId", sorted, for before/after comparisons. */
    protected List<String> connectionRows() {
        return jdbc.queryForList(
                "SELECT id || '|' || source_name || '|' || target_name || '|' || name || '|' || coalesce(kind, '-')"
                        + " || '|' || coalesce(cast(source_id AS text), '-') || '|'"
                        + " || coalesce(cast(target_id AS text), '-') FROM connection ORDER BY 1",
                String.class);
    }

    protected String flow(String name, String color) throws Exception {
        MvcResult r = mvc.perform(post("/api/flows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("name", name, "color", color))))
                .andReturn();
        if (r.getResponse().getStatus() != 201) {
            throw new AssertionError(name + " -> " + r.getResponse().getContentAsString());
        }
        return read(r).get("flow").get("id").asText();
    }

    // ---------- connections document ----------

    protected MvcResult previewDocument(String text) throws Exception {
        return mvc.perform(post("/api/connections-document/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("text", text))))
                .andReturn();
    }

    protected MvcResult applyDocument(String text, Long baseRevision) throws Exception {
        Map<String, Object> req = new HashMap<>();
        req.put("text", text);
        req.put("baseRevision", baseRevision);
        return mvc.perform(put("/api/connections-document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(req)))
                .andReturn();
    }

    protected JsonNode exportDocument() throws Exception {
        return read(mvc.perform(get("/api/connections-document")).andReturn());
    }

    // ---------- canvas ----------

    protected JsonNode canvas() throws Exception {
        return read(mvc.perform(get("/api/canvas")).andReturn());
    }

    protected JsonNode node(JsonNode canvas, String id) {
        for (JsonNode n : canvas.get("nodes")) {
            if (n.get("id").asText().equals(id)) {
                return n;
            }
        }
        return null;
    }
}
