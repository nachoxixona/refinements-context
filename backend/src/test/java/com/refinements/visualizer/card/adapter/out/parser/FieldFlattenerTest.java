package com.refinements.visualizer.card.adapter.out.parser;

import static org.assertj.core.api.Assertions.assertThat;

import com.refinements.visualizer.card.adapter.out.fetch.SpecFetcher;
import com.refinements.visualizer.card.model.Field;
import com.refinements.visualizer.card.model.FieldLocation;
import com.refinements.visualizer.card.model.HttpMethod;
import com.refinements.visualizer.card.model.ParsedEndpoint;
import com.refinements.visualizer.support.TestSpecs;
import java.util.List;
import org.junit.jupiter.api.Test;

class FieldFlattenerTest {

    private final SpecParser parser = new SpecParser(new SpecFetcher(TestSpecs.props()), TestSpecs.props());
    private final FieldFlattener flattener = new FieldFlattener();

    private List<ParsedEndpoint> flatten(String fixture) {
        return flattener.flatten(parser.parse(TestSpecs.read(fixture), null).openApi(), "svc");
    }

    private static ParsedEndpoint endpoint(List<ParsedEndpoint> endpoints, HttpMethod method, String path) {
        return endpoints.stream()
                .filter(e -> e.method() == method && e.path().equals(path))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void parametersAndBodyBecomeInputsAndCookiesAreSkipped() {
        ParsedEndpoint get = endpoint(flatten("oas30/simple.yaml"), HttpMethod.GET, "/widgets/{widgetId}");
        assertThat(get.inputs())
                .extracting(Field::name, Field::location)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("widgetId", FieldLocation.PATH),
                        org.assertj.core.groups.Tuple.tuple("verbose", FieldLocation.QUERY),
                        org.assertj.core.groups.Tuple.tuple("X-Trace", FieldLocation.HEADER));
        assertThat(get.inputs().get(0).required()).isTrue();
        assertThat(get.inputs().get(0).type()).isEqualTo("integer");

        ParsedEndpoint post = endpoint(flatten("reference-workspace/order-service.yaml"), HttpMethod.POST, "/orders");
        assertThat(post.inputs())
                .extracting(Field::path)
                .containsExactly("customerId", "items", "items[].productId", "items[].quantity");
        assertThat(post.inputs()).allMatch(f -> f.location() == FieldLocation.BODY);
        assertThat(post.inputs().get(0).required()).isTrue();
    }

    @Test
    void outputsComeFromTheLowest2xxResponse() {
        ParsedEndpoint get =
                endpoint(flatten("reference-workspace/order-service.yaml"), HttpMethod.GET, "/orders/{orderId}");
        assertThat(get.outputs()).extracting(Field::path).doesNotContain("error", "message");
        assertThat(get.outputs()).allMatch(f -> f.location() == FieldLocation.RESPONSE);
        // 200 is the lowest 2xx (204 is ignored), and application/json beats the other media types
        assertThat(endpoint(flatten("oas30/simple.yaml"), HttpMethod.GET, "/widgets/{widgetId}")
                        .outputs())
                .extracting(Field::path)
                .containsExactly("widgetId", "name");
        assertThat(endpoint(flatten("oas30/simple.yaml"), HttpMethod.DELETE, "/widgets/{widgetId}")
                        .outputs())
                .isEmpty();
    }

    @Test
    void mediaTypesArePreferredInOrder() {
        List<ParsedEndpoint> endpoints = flatten("oas30/simple.yaml");
        // no application/json: the first JSON-like type wins over application/xml
        assertThat(endpoint(endpoints, HttpMethod.POST, "/alpha").inputs())
                .extracting(Field::name)
                .containsExactly("jsonLike");
    }

    @Test
    void arrayItemsGetTheBracketSuffix() {
        ParsedEndpoint get =
                endpoint(flatten("reference-workspace/order-service.yaml"), HttpMethod.GET, "/orders/{orderId}");
        assertThat(get.outputs()).extracting(Field::path).contains("items", "items[].productId");
        Field items = get.outputs().stream()
                .filter(f -> f.path().equals("items"))
                .findFirst()
                .orElseThrow();
        assertThat(items.type()).isEqualTo("array");
        // a top-level array response puts its item fields under []
        ParsedEndpoint list =
                endpoint(flatten("reference-workspace/customer-service.json"), HttpMethod.GET, "/customers");
        assertThat(list.outputs()).extracting(Field::path).contains("[].email");
    }

    @Test
    void allOfIsMergedAndOneOfBranchesAreTagged() {
        ParsedEndpoint get =
                endpoint(flatten("reference-workspace/order-service.yaml"), HttpMethod.GET, "/orders/{orderId}");
        assertThat(get.outputs())
                .extracting(Field::path)
                .containsExactly(
                        "customerId",
                        "items",
                        "items[].productId",
                        "items[].quantity",
                        "id",
                        "status",
                        "total",
                        "createdAt");

        ParsedEndpoint payment = endpoint(flatten("refs/chains.yaml"), HttpMethod.POST, "/payments");
        assertThat(payment.inputs())
                .extracting(Field::name, Field::variant)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("cardNumber", 0),
                        org.assertj.core.groups.Tuple.tuple("iban", 1));
    }

    @Test
    void recursionStopsWithTruncatedAtTheDepthLimit() {
        ParsedEndpoint payment = endpoint(flatten("refs/chains.yaml"), HttpMethod.POST, "/payments");
        List<Field> outputs = payment.outputs();
        assertThat(outputs).extracting(Field::path).startsWith("label", "children", "children[].label");
        Field truncated = outputs.stream().filter(Field::truncated).findFirst().orElseThrow();
        assertThat(truncated.name()).isEqualTo("children");
        int depth = truncated.path().split("\\.").length;
        assertThat(depth).isLessThanOrEqualTo(FieldFlattener.MAX_DEPTH);
        assertThat(outputs).noneMatch(f -> f.path().split("\\.").length > FieldFlattener.MAX_DEPTH);
    }

    @Test
    void endpointsAreSortedByPathThenMethod() {
        List<ParsedEndpoint> endpoints = flatten("oas30/simple.yaml");
        assertThat(endpoints)
                .extracting(e -> e.method() + " " + e.path())
                .containsExactly("POST /alpha", "GET /widgets/{widgetId}", "DELETE /widgets/{widgetId}");
        assertThat(endpoints.get(1).endpointKey()).isEqualTo("svc:GET /widgets/{}");
    }
}
