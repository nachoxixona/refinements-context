package com.refinements.visualizer.card.adapter.out.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.refinements.visualizer.card.adapter.out.fetch.SpecFetcher;
import com.refinements.visualizer.card.model.Field;
import com.refinements.visualizer.card.model.ParsedEndpoint;
import com.refinements.visualizer.card.model.SpecFormat;
import com.refinements.visualizer.shared.errors.ParseError;
import com.refinements.visualizer.shared.errors.SpecInvalidException;
import com.refinements.visualizer.support.TestSpecs;
import io.swagger.v3.oas.models.media.Schema;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpecParserTest {

    private final SpecParser parser = new SpecParser(new SpecFetcher(TestSpecs.props()), TestSpecs.props());
    private final FieldFlattener flattener = new FieldFlattener();

    private ParsedSpec parse(String fixture) {
        return parser.parse(TestSpecs.read(fixture), null);
    }

    @Test
    void swagger2YamlAndJsonBecomeOpenApi3() {
        ParsedSpec yaml = parse("swagger2/catalog.yaml");
        ParsedSpec json = parse("swagger2/catalog.json");
        assertThat(yaml.format()).isEqualTo(SpecFormat.SWAGGER_2_0);
        assertThat(json.format()).isEqualTo(SpecFormat.SWAGGER_2_0);
        assertThat(yaml.openApi().getOpenapi()).startsWith("3.");
        assertThat(yaml.title()).isEqualTo("Catalog Service");
        assertThat(flattener.flatten(yaml.openApi(), "c")).isEqualTo(flattener.flatten(json.openApi(), "c"));
        // definitions/Product and the nested Money ref are inlined
        List<String> outputs =
                paths(flattener.flatten(yaml.openApi(), "c").get(0).outputs());
        assertThat(outputs).contains("id", "name", "price", "price.amount", "price.currency");
    }

    @Test
    void openApi30And31Parse() {
        assertThat(parse("oas30/simple.yaml").format()).isEqualTo(SpecFormat.OPENAPI_3_0);
        ParsedSpec oas31 = parse("oas31/simple.json");
        assertThat(oas31.format()).isEqualTo(SpecFormat.OPENAPI_3_1);
        List<String> outputs =
                paths(flattener.flatten(oas31.openApi(), "s").get(0).outputs());
        assertThat(outputs).containsExactly("itemId", "note", "tags", "tags[].tagCode");
    }

    @Test
    void refChainsAreFullyInlined() {
        ParsedSpec spec = parse("refs/chains.yaml");
        spec.openApi().getPaths().values().forEach(item -> item.readOperations().forEach(op -> {
            op.getResponses().values().forEach(r -> {
                assertThat(r.get$ref()).isNull();
                if (r.getContent() != null) {
                    r.getContent().values().forEach(m -> assertNoRef(m.getSchema(), 0));
                }
            });
        }));
        List<ParsedEndpoint> endpoints = flattener.flatten(spec.openApi(), "r");
        ParsedEndpoint account = endpoints.stream()
                .filter(e -> e.path().startsWith("/accounts"))
                .findFirst()
                .orElseThrow();
        assertThat(account.inputs()).extracting(Field::name).containsExactly("accountId");
        assertThat(account.inputs().get(0).type()).isEqualTo("string");
        assertThat(paths(account.outputs())).contains("accountId", "owner", "owner.ownerId", "owner.address.city");
    }

    @Test
    void multiFileRelativeRefsResolveAgainstTheSpecUrl() {
        try (TestSpecs.SpecServer server = new TestSpecs.SpecServer()) {
            String url = server.url("refs/multi-file/root.yaml");
            ParsedSpec spec = parser.parse(TestSpecs.read("refs/multi-file/root.yaml"), URI.create(url));
            List<ParsedEndpoint> endpoints = flattener.flatten(spec.openApi(), "m");
            assertThat(paths(endpoints.get(0).outputs()))
                    .containsExactly(
                            "orderId", "customer", "customer.customerId", "customer.email", "lines", "lines[].sku");
        }
    }

    @Test
    void relativeRefInPastedSpecIsReportedWithLocation() {
        assertThatThrownBy(() -> parse("refs/relative-pasted.yaml"))
                .isInstanceOf(SpecInvalidException.class)
                .satisfies(e -> {
                    ParseError err = ((SpecInvalidException) e).getErrors().get(0);
                    assertThat(err.location()).contains("paths./things.get.responses.200");
                    assertThat(err.message()).contains("pasted spec").contains("by URL");
                });
    }

    @Test
    void refToAnotherHostIsBlockedUnlessAllowlisted() {
        try (TestSpecs.SpecServer server = new TestSpecs.SpecServer()) {
            String url = server.url("refs/cross-host/root.yaml");
            assertThatThrownBy(() -> parser.parse(TestSpecs.read("refs/cross-host/root.yaml"), URI.create(url)))
                    .isInstanceOf(SpecInvalidException.class)
                    .satisfies(e -> {
                        ParseError err = ((SpecInvalidException) e).getErrors().get(0);
                        assertThat(err.location()).isNotBlank();
                        assertThat(err.message()).contains("other-host.invalid").contains("not allowed");
                    });
        }
    }

    @Test
    void missingRefIsReportedWithLocation() {
        assertThatThrownBy(() -> parse("invalid/missing-ref.yaml"))
                .isInstanceOf(SpecInvalidException.class)
                .satisfies(e -> {
                    ParseError err = ((SpecInvalidException) e).getErrors().get(0);
                    assertThat(err.location())
                            .isEqualTo("paths./things.get.responses.200.content.application/json.schema");
                    assertThat(err.message()).contains("#/components/schemas/Missing");
                });
    }

    @Test
    void nonSpecTextIsRejected() {
        assertThatThrownBy(() -> parse("invalid/not-a-spec.txt"))
                .isInstanceOf(SpecInvalidException.class)
                .hasMessageContaining("not a valid");
        assertThatThrownBy(() -> parse("invalid/malformed.yaml"))
                .isInstanceOf(SpecInvalidException.class)
                .satisfies(e -> assertThat(
                                ((SpecInvalidException) e).getErrors().get(0).location())
                        .startsWith("line"));
        assertThatThrownBy(() -> parser.parse("   ", null)).isInstanceOf(SpecInvalidException.class);
    }

    @Test
    void emptyPathsIsAcceptedWithNoEndpoints() {
        ParsedSpec spec = parse("oas30/empty-paths.yaml");
        assertThat(flattener.flatten(spec.openApi(), "e")).isEmpty();
    }

    @Test
    void sameInputGivesSameOutput() {
        String content = TestSpecs.read("reference-workspace/order-service.yaml");
        List<ParsedEndpoint> first =
                flattener.flatten(parser.parse(content, null).openApi(), "o");
        List<ParsedEndpoint> second =
                flattener.flatten(parser.parse(content, null).openApi(), "o");
        assertThat(first).isEqualTo(second);
    }

    private static List<String> paths(List<Field> fields) {
        return fields.stream().map(Field::path).toList();
    }

    @SuppressWarnings("rawtypes")
    private static void assertNoRef(Schema<?> schema, int depth) {
        if (schema == null || depth > 8) {
            return;
        }
        if (schema.get$ref() != null) {
            // only the recursion marker of a recursive component may remain
            assertThat(schema.get$ref()).isEqualTo("#/components/schemas/TreeNode");
            return;
        }
        if (schema.getProperties() != null) {
            for (Schema s : schema.getProperties().values()) {
                assertNoRef(s, depth + 1);
            }
        }
        if (schema.getItems() != null) {
            assertNoRef(schema.getItems(), depth + 1);
        }
    }
}
