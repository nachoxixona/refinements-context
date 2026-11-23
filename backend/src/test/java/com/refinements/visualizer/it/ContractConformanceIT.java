package com.refinements.visualizer.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Fails when the runtime API (springdoc) drifts from specs/004-meeting-tools/contracts/openapi.yaml
 * (constitution: API contracts). Paths and methods must match exactly; every contract component schema must
 * exist at runtime, except the Problem Details shapes and enums that springdoc inlines.
 */
class ContractConformanceIT extends PostgresIntegrationTest {

    /** Problem Details shapes, enums and request-only shapes that springdoc inlines or names differently. */
    private static final Set<String> INLINED = Set.of(
            "Problem",
            "ConflictProblem",
            "ParseError",
            "SpecProblem",
            "DuplicateCardProblem",
            "ConnectionsDocumentProblem",
            "DocumentError",
            "HttpMethod",
            "SpecFormat",
            "ConnectionKind",
            "FlowColor",
            "NoteKind",
            "LayoutUpdate",
            "ScenarioRemovals",
            "ConnectionChange");

    @Test
    void runtimeApiMatchesTheContract() throws Exception {
        Path contractFile = Path.of("../specs/004-meeting-tools/contracts/openapi.yaml");
        JsonNode contract = new ObjectMapper(new YAMLFactory()).readTree(Files.readString(contractFile));
        JsonNode runtime = json.readTree(
                mvc.perform(get("/v3/api-docs/api")).andReturn().getResponse().getContentAsString());

        assertThat(operations(runtime.get("paths"), "")).isEqualTo(operations(contract.get("paths"), "/api"));

        Set<String> runtimeSchemas = names(runtime.path("components").path("schemas"));
        Set<String> missing = new TreeSet<>(names(contract.path("components").path("schemas")));
        missing.removeAll(runtimeSchemas);
        missing.removeAll(INLINED);
        assertThat(missing).as("contract schemas missing at runtime").isEmpty();
    }

    /** The schema served at /connections-document.schema.json is the contract file (FR-029, tasks.md T086). */
    @Test
    void servedConnectionsDocumentSchemaMatchesTheContract() throws Exception {
        String contract =
                Files.readString(Path.of("../specs/004-meeting-tools/contracts/connections-document.schema.json"));
        String served = mvc.perform(get("/connections-document.schema.json"))
                .andReturn()
                .getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(json.readTree(served)).isEqualTo(json.readTree(contract));
    }

    private static Set<String> operations(JsonNode paths, String prefix) {
        Set<String> ops = new TreeSet<>();
        for (Iterator<String> it = paths.fieldNames(); it.hasNext(); ) {
            String path = it.next();
            for (Iterator<String> m = paths.get(path).fieldNames(); m.hasNext(); ) {
                String method = m.next();
                if (Set.of("get", "post", "put", "delete", "patch").contains(method)) {
                    ops.add(method.toUpperCase() + " " + prefix + path);
                }
            }
        }
        return ops;
    }

    private static Set<String> names(JsonNode node) {
        Set<String> out = new TreeSet<>();
        node.fieldNames().forEachRemaining(out::add);
        return out;
    }
}
