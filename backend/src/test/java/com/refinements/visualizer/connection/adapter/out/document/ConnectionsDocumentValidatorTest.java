package com.refinements.visualizer.connection.adapter.out.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.refinements.visualizer.connection.ConnectionKind;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentModel;
import com.refinements.visualizer.shared.VisualizerProperties;
import com.refinements.visualizer.shared.errors.ConnectionsDocumentInvalidException;
import com.refinements.visualizer.shared.errors.DocumentError;
import com.refinements.visualizer.shared.errors.DocumentError.Kind;
import com.refinements.visualizer.support.TestSpecs;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** research R4, R6 and data-model.md §4 (Connection.name, Connection.kind, Connection.description, Microservice.name/description). */
class ConnectionsDocumentValidatorTest {

    private final ConnectionsDocumentReader reader = new ConnectionsDocumentReader(TestSpecs.props());

    static String fixture(String name) {
        try (var in = ConnectionsDocumentValidatorTest.class.getResourceAsStream("/connections/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<DocumentError> errors(String text) {
        try {
            reader.read(text);
        } catch (ConnectionsDocumentInvalidException e) {
            return e.getErrors();
        }
        throw new AssertionError("expected the document to be rejected");
    }

    @Test
    void readsAValidDocument() {
        ConnectionsDocumentModel m = reader.read(fixture("shop.json"));
        assertThat(m.microservices()).hasSize(5);
        assertThat(m.connections()).hasSize(7);
        assertThat(m.connections().get(1).kind()).isEqualTo(ConnectionKind.EVENT);
        assertThat(m.connections().get(5).kind()).isNull();
        assertThat(m.microservices().get(1).descriptionPresent()).isFalse();
        assertThat(reader.read("{\"formatVersion\":2,\"connections\":[]}").connections())
                .isEmpty();
    }

    @Test
    void everyErrorKindWithExactPositionsInDocumentOrder() {
        List<DocumentError> e = errors(fixture("invalid-mixed.json"));
        assertThat(e)
                .extracting(DocumentError::kind)
                .containsExactly(
                        Kind.DUPLICATE_MICROSERVICE,
                        Kind.MISSING,
                        Kind.MISSING,
                        Kind.SELF_CONNECTION,
                        Kind.INVALID_KIND,
                        Kind.DUPLICATE_CONNECTION,
                        Kind.UNKNOWN_PROPERTY,
                        Kind.WRONG_TYPE,
                        Kind.TOO_LONG);
        DocumentError dupMs = e.get(0);
        assertThat(dupMs.path()).isEqualTo("microservices[1].name");
        assertThat(dupMs.pointer()).isEqualTo("/microservices/1/name");
        assertThat(dupMs.line()).isEqualTo(5);
        assertThat(dupMs.column()).isEqualTo(7);
        DocumentError missingTarget = e.get(2);
        assertThat(missingTarget.path()).isEqualTo("connections[1].target");
        assertThat(missingTarget.message()).isEqualTo("connections[1].target is missing");
        assertThat(missingTarget.line()).isEqualTo(10);
        DocumentError dupConnection = e.get(5);
        assertThat(dupConnection.message()).contains("connections[0]");
        DocumentError unknown = e.get(6);
        assertThat(unknown.path()).isEqualTo("connections[5].lable");
        assertThat(unknown.line()).isEqualTo(14);
    }

    @Test
    void syntaxVersionTypeAndLimits() {
        List<DocumentError> syntax = errors(fixture("invalid-syntax.json"));
        assertThat(syntax).singleElement().satisfies(d -> {
            assertThat(d.kind()).isEqualTo(Kind.SYNTAX);
            assertThat(d.line()).isEqualTo(5);
        });
        assertThat(errors("")).extracting(DocumentError::kind).containsExactly(Kind.SYNTAX);
        assertThat(errors("{\"a\":1,\"a\":2}")).extracting(DocumentError::kind).contains(Kind.SYNTAX);
        assertThat(errors("[]")).extracting(DocumentError::kind).containsExactly(Kind.WRONG_TYPE);
        assertThat(errors("{\"formatVersion\":3,\"connections\":[]}"))
                .extracting(DocumentError::kind)
                .containsExactly(Kind.UNSUPPORTED_VERSION);
        assertThat(errors("{\"connections\":[]}"))
                .extracting(DocumentError::kind)
                .containsExactly(Kind.UNSUPPORTED_VERSION);
        assertThat(errors("{\"formatVersion\":2}"))
                .extracting(DocumentError::kind)
                .containsExactly(Kind.MISSING);
        assertThat(errors("{\"formatVersion\":2,\"connections\":{}}"))
                .extracting(DocumentError::kind)
                .containsExactly(Kind.WRONG_TYPE);
        assertThat(errors("{\"formatVersion\":2,\"connections\":[],\"microservices\":[{\"name\":\"A\",\"description\":"
                        + "\"" + "x".repeat(2001) + "\"}]}"))
                .extracting(DocumentError::kind)
                .containsExactly(Kind.TOO_LONG);

        VisualizerProperties small = new VisualizerProperties(
                TestSpecs.props().spec(),
                new VisualizerProperties.ConnectionsDocument(1024 * 1024, 1, 1),
                TestSpecs.props().http(),
                TestSpecs.props().fetch(),
                TestSpecs.props().refs());
        assertThatThrownBy(() -> new ConnectionsDocumentReader(small)
                        .read(
                                "{\"formatVersion\":2,\"connections\":[{\"name\":\"a\",\"source\":\"x\",\"target\":\"y\"},"
                                        + "{\"name\":\"b\",\"source\":\"x\",\"target\":\"y\"}]}"))
                .isInstanceOfSatisfying(ConnectionsDocumentInvalidException.class, ex -> assertThat(ex.getErrors())
                        .extracting(DocumentError::kind)
                        .containsExactly(Kind.TOO_MANY));
    }

    @Test
    void flowErrors() {
        List<DocumentError> e = errors("{\"formatVersion\":2,"
                + "\"flows\":[{\"name\":\"Checkout\",\"color\":\"blue\"},{\"name\":\"checkout\",\"color\":\"pink\"},"
                + "{\"name\":\"X\",\"color\":\"green\"}],"
                + "\"connections\":[{\"name\":\"a\",\"source\":\"A\",\"target\":\"B\",\"flow\":\"Nope\"}]}");
        assertThat(e)
                .extracting(DocumentError::kind)
                .containsExactly(Kind.DUPLICATE_FLOW, Kind.INVALID_COLOR, Kind.UNKNOWN_FLOW);
        assertThat(e.get(1).message()).contains("blue").contains("slate");
        ConnectionsDocumentModel ok = reader.read(
                "{\"formatVersion\":2,\"flows\":[{\"name\":\"Checkout\",\"color\":\"blue\"}],"
                        + "\"connections\":[{\"name\":\"a\",\"source\":\"A\",\"target\":\"B\",\"flow\":\" CHECKOUT\"}]}");
        assertThat(ok.connections().get(0).flow()).isEqualTo("Checkout");
    }

    @Test
    void endpointsAreNormalizedAndMalformedOnesRejected() {
        ConnectionsDocumentModel ok = reader.read("{\"formatVersion\":2,\"connections\":["
                + "{\"name\":\"a\",\"source\":\"A\",\"target\":\"B\",\"endpoint\":\" post  /invoices/{id} \"}]}");
        assertThat(ok.connections().get(0).endpoint()).isEqualTo("POST /invoices/{id}");
        List<DocumentError> e = errors("{\"formatVersion\":2,\"connections\":["
                + "{\"name\":\"a\",\"source\":\"A\",\"target\":\"B\",\"endpoint\":\"FETCH /x\"},"
                + "{\"name\":\"b\",\"source\":\"A\",\"target\":\"B\",\"endpoint\":\"GET invoices\"},"
                + "{\"name\":\"c\",\"source\":\"A\",\"target\":\"B\",\"endpoint\":42}]}");
        assertThat(e)
                .extracting(DocumentError::kind)
                .containsOnly(Kind.INVALID_ENDPOINT)
                .hasSize(3);
        assertThat(e.get(0).path()).isEqualTo("connections[0].endpoint");
    }

    @Test
    void rejectsVersion1Documents() {
        assertThat(errors("{\"formatVersion\":1,\"connections\":[]}"))
                .extracting(DocumentError::kind)
                .contains(Kind.UNSUPPORTED_VERSION);
    }

    @Test
    void namesCompareNormalized() {
        List<DocumentError> e = errors("{\"formatVersion\":2,\"connections\":["
                + "{\"name\":\"Order  placed\",\"source\":\"Orders\",\"target\":\"Billing\"},"
                + "{\"name\":\"order placed\",\"source\":\" orders\",\"target\":\"BILLING\"},"
                + "{\"name\":\"x\",\"source\":\"A b\",\"target\":\"a  B\"}]}");
        assertThat(e).extracting(DocumentError::kind).containsExactly(Kind.DUPLICATE_CONNECTION, Kind.SELF_CONNECTION);
    }
}
