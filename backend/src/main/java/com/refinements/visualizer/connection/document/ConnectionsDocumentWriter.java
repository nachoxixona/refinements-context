package com.refinements.visualizer.connection.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.refinements.visualizer.connection.Connection;
import com.refinements.visualizer.connection.Flow;
import com.refinements.visualizer.microservice.MicroserviceView;
import com.refinements.visualizer.shared.Names;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Deterministic export (research R4): microservices sorted by normalized name, attached connections by (source,
 * target, name), absent optional properties omitted, two-space indent, trailing newline. Dangling connections can't
 * be expressed and are left out (FR-025).
 */
@Component
public class ConnectionsDocumentWriter {

    public record Export(ConnectionsDocumentDto document, String text, int excludedDanglingConnections) {}

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
        this.writer = new ObjectMapper().writer(printer);
    }

    public Export export(List<MicroserviceView> microservices, List<Connection> connections) {
        return export(microservices, List.of(), connections);
    }

    public Export export(List<MicroserviceView> microservices, List<Flow> flows, List<Connection> connections) {
        List<Flow> gs = new ArrayList<>(flows);
        gs.sort(Comparator.comparing(Flow::getNameNormalized));
        Map<UUID, String> flowNames = new HashMap<>();
        List<ConnectionsDocumentDto.FlowDoc> flowDocs = new ArrayList<>();
        for (Flow g : gs) {
            flowNames.put(g.getId(), g.getName());
            flowDocs.add(
                    new ConnectionsDocumentDto.FlowDoc(g.getName(), g.getColor().documentValue()));
        }
        List<MicroserviceView> ms = new ArrayList<>(microservices);
        ms.sort(Comparator.comparing(MicroserviceView::nameNormalized));
        List<ConnectionsDocumentDto.MicroserviceDoc> msDocs = new ArrayList<>();
        for (MicroserviceView m : ms) {
            msDocs.add(new ConnectionsDocumentDto.MicroserviceDoc(
                    m.name(), m.description(), m.team(), m.repositoryUrl(), m.docsUrl()));
        }
        List<Connection> attached = new ArrayList<>();
        int dangling = 0;
        for (Connection f : connections) {
            if (f.isDangling()) {
                dangling++;
            } else {
                attached.add(f);
            }
        }
        attached.sort(Comparator.comparing(Connection::getSourceNameNormalized)
                .thenComparing(Connection::getTargetNameNormalized)
                .thenComparing(Connection::getNameNormalized));
        List<ConnectionsDocumentDto.ConnectionDoc> connectionDocs = new ArrayList<>();
        for (Connection f : attached) {
            connectionDocs.add(new ConnectionsDocumentDto.ConnectionDoc(
                    f.getName(),
                    f.getSourceName(),
                    f.getTargetName(),
                    f.getKind() == null ? null : f.getKind().documentValue(),
                    f.getEndpoint(),
                    f.getFlowId() == null ? null : flowNames.get(f.getFlowId()),
                    f.getFlowId() == null ? null : f.getStep(),
                    f.getDescription()));
        }
        ConnectionsDocumentDto doc = new ConnectionsDocumentDto(
                ConnectionsDocumentReader.FORMAT_VERSION,
                msDocs.isEmpty() ? null : List.copyOf(msDocs),
                flowDocs.isEmpty() ? null : List.copyOf(flowDocs),
                List.copyOf(connectionDocs));
        return new Export(doc, write(doc), dangling);
    }

    public String write(ConnectionsDocumentDto doc) {
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
