package com.refinements.visualizer.connection.domain.document;

import com.refinements.visualizer.connection.domain.Connection;
import com.refinements.visualizer.connection.domain.Flow;
import com.refinements.visualizer.microservice.MicroserviceView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic export content (research R4): microservices sorted by normalized name, flows by normalized name,
 * attached connections by (source, target, name). Dangling connections can't be expressed and are left out
 * (FR-025).
 */
public final class ConnectionsDocumentExport {

    public record Exported(ConnectionsDocument document, int excludedDanglingConnections) {}

    private ConnectionsDocumentExport() {}

    public static Exported of(List<MicroserviceView> microservices, List<Flow> flows, List<Connection> connections) {
        List<Flow> gs = new ArrayList<>(flows);
        gs.sort(Comparator.comparing(Flow::getNameNormalized));
        Map<UUID, String> flowNames = new HashMap<>();
        List<ConnectionsDocument.FlowDoc> flowDocs = new ArrayList<>();
        for (Flow g : gs) {
            flowNames.put(g.getId(), g.getName());
            flowDocs.add(
                    new ConnectionsDocument.FlowDoc(g.getName(), g.getColor().documentValue()));
        }
        List<MicroserviceView> ms = new ArrayList<>(microservices);
        ms.sort(Comparator.comparing(MicroserviceView::nameNormalized));
        List<ConnectionsDocument.MicroserviceDoc> msDocs = new ArrayList<>();
        for (MicroserviceView m : ms) {
            msDocs.add(new ConnectionsDocument.MicroserviceDoc(
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
        List<ConnectionsDocument.ConnectionDoc> connectionDocs = new ArrayList<>();
        for (Connection f : attached) {
            connectionDocs.add(new ConnectionsDocument.ConnectionDoc(
                    f.getName(),
                    f.getSourceName(),
                    f.getTargetName(),
                    f.getKind() == null ? null : f.getKind().documentValue(),
                    f.getEndpoint(),
                    f.getFlowId() == null ? null : flowNames.get(f.getFlowId()),
                    f.getFlowId() == null ? null : f.getStep(),
                    f.getDescription()));
        }
        ConnectionsDocument doc = new ConnectionsDocument(
                ConnectionsDocument.FORMAT_VERSION,
                msDocs.isEmpty() ? null : List.copyOf(msDocs),
                flowDocs.isEmpty() ? null : List.copyOf(flowDocs),
                List.copyOf(connectionDocs));
        return new Exported(doc, dangling);
    }
}
