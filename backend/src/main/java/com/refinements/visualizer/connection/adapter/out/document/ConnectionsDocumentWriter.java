package com.refinements.visualizer.connection.adapter.out.document;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.refinements.visualizer.connection.domain.Connection;
import com.refinements.visualizer.connection.domain.Flow;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocument;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentExport;
import com.refinements.visualizer.microservice.MicroserviceView;
import com.refinements.visualizer.shared.Names;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Writes the connections document text (research R4): properties in declaration order, absent optional
 * properties omitted, two-space indent, trailing newline. The content and its order come from
 * {@link ConnectionsDocumentExport}.
 */
@Component
public class ConnectionsDocumentWriter {

    public record Export(ConnectionsDocument document, String text, int excludedDanglingConnections) {}

    private final ObjectWriter writer;

    public ConnectionsDocumentWriter() {
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withSeparators(Separators.createDefaultInstance()
                        .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                        .withObjectEmptySeparator("")
                        .withArrayEmptySeparator(""));
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        this.writer = new ObjectMapper()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .writer(printer);
    }

    public Export export(List<MicroserviceView> microservices, List<Connection> connections) {
        return export(microservices, List.of(), connections);
    }

    public Export export(List<MicroserviceView> microservices, List<Flow> flows, List<Connection> connections) {
        ConnectionsDocumentExport.Exported e = ConnectionsDocumentExport.of(microservices, flows, connections);
        return new Export(e.document(), write(e.document()), e.excludedDanglingConnections());
    }

    public String write(ConnectionsDocument doc) {
        try {
            return writer.writeValueAsString(doc) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Sort key used by the writer, exposed for tests. */
    static String key(String name) {
        return Names.norm(name);
    }
}
