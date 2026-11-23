package com.refinements.visualizer.connection.domain.document;

import com.refinements.visualizer.connection.ConnectionKey;
import com.refinements.visualizer.connection.ConnectionKind;
import com.refinements.visualizer.connection.FlowColor;
import com.refinements.visualizer.connection.domain.Connection;
import com.refinements.visualizer.connection.domain.Flow;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentChanges.ConnectionChangeSummary;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentChanges.MicroserviceChange;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentModel.ConnectionEntry;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentModel.MicroserviceEntry;
import com.refinements.visualizer.microservice.MicroserviceView;
import com.refinements.visualizer.shared.Names;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Diff of a document against the baseline (research R5). Connections are matched by their (source, target, name)
 * key, dangling ones included; the document replaces the baseline connections. Microservices are never removed.
 */
public final class ConnectionsDocumentDiff {

    /** A matched connection whose name casing, kind or description changes. */
    public record ConnectionUpdate(Connection connection, ConnectionEntry entry) {}

    /** A listed microservice whose description or ownership changes; absent properties stay as they are. */
    public record Describe(MicroserviceView microservice, MicroserviceEntry entry) {

        public String description() {
            return entry.descriptionPresent() ? entry.description() : microservice.description();
        }

        public String team() {
            return entry.teamPresent() ? entry.team() : microservice.team();
        }

        public String repository() {
            return entry.repositoryPresent() ? entry.repository() : microservice.repositoryUrl();
        }

        public String docs() {
            return entry.docsPresent() ? entry.docs() : microservice.docsUrl();
        }
    }

    /** A listed flow to create or recolor. */
    public record FlowUpsert(Flow existing, String name, FlowColor color) {}

    public record Plan(
            List<FlowUpsert> flows,
            List<String> microservicesToCreate,
            Map<String, MicroserviceEntry> createEntries,
            List<Describe> describe,
            List<ConnectionEntry> connectionsToAdd,
            List<ConnectionUpdate> connectionsToUpdate,
            List<Connection> connectionsToRemove,
            ConnectionsDocumentChanges changes) {}

    private ConnectionsDocumentDiff() {}

    public static Plan diff(
            ConnectionsDocumentModel doc, List<MicroserviceView> microservices, List<Connection> connections) {
        return diff(doc, microservices, List.of(), connections);
    }

    public static Plan diff(
            ConnectionsDocumentModel doc,
            List<MicroserviceView> microservices,
            List<Flow> flows,
            List<Connection> connections) {
        Map<String, Flow> flowsByName = new LinkedHashMap<>();
        Map<UUID, String> flowNameById = new HashMap<>();
        for (Flow g : flows) {
            flowsByName.put(g.getNameNormalized(), g);
            flowNameById.put(g.getId(), g.getName());
        }
        List<FlowUpsert> upserts = new ArrayList<>();
        List<ConnectionsDocumentChanges.FlowChange> flowsAdded = new ArrayList<>();
        List<ConnectionsDocumentChanges.FlowChange> flowsChanged = new ArrayList<>();
        for (ConnectionsDocumentModel.FlowEntry g : doc.flows()) {
            Flow existing = flowsByName.get(Names.norm(g.name()));
            if (existing == null) {
                upserts.add(new FlowUpsert(null, g.name(), g.color()));
                flowsAdded.add(new ConnectionsDocumentChanges.FlowChange(
                        g.name(), null, g.color().documentValue()));
            } else if (existing.getColor() != g.color()) {
                upserts.add(new FlowUpsert(existing, g.name(), g.color()));
                flowsChanged.add(new ConnectionsDocumentChanges.FlowChange(
                        existing.getName(),
                        existing.getColor().documentValue(),
                        g.color().documentValue()));
            }
        }
        Map<String, MicroserviceView> byName = new LinkedHashMap<>();
        microservices.forEach(m -> byName.put(m.nameNormalized(), m));

        // microservices: listed entries first (their spelling wins), then connection ends, in document order
        Map<String, String> toCreate = new LinkedHashMap<>();
        Map<String, MicroserviceEntry> createEntries = new LinkedHashMap<>();
        List<Describe> describe = new ArrayList<>();
        List<MicroserviceChange> msChanged = new ArrayList<>();
        for (MicroserviceEntry e : doc.microservices()) {
            String norm = Names.norm(e.name());
            MicroserviceView existing = byName.get(norm);
            if (existing == null) {
                toCreate.putIfAbsent(norm, e.name());
                createEntries.putIfAbsent(norm, e);
            } else {
                Describe d = new Describe(existing, e);
                if (!Objects.equals(existing.description(), d.description())
                        || !Objects.equals(existing.team(), d.team())
                        || !Objects.equals(existing.repositoryUrl(), d.repository())
                        || !Objects.equals(existing.docsUrl(), d.docs())) {
                    describe.add(d);
                    msChanged.add(new MicroserviceChange(
                            existing.name(),
                            existing.description(),
                            d.description(),
                            existing.team(),
                            d.team(),
                            existing.repositoryUrl(),
                            d.repository(),
                            existing.docsUrl(),
                            d.docs()));
                }
            }
        }
        for (ConnectionEntry f : doc.connections()) {
            for (String end : List.of(f.source(), f.target())) {
                String norm = Names.norm(end);
                if (!byName.containsKey(norm)) {
                    toCreate.putIfAbsent(norm, end);
                }
            }
        }

        Map<ConnectionKey, Connection> baseline = new LinkedHashMap<>();
        connections.forEach(f -> baseline.put(f.key(), f));
        List<ConnectionEntry> add = new ArrayList<>();
        List<ConnectionUpdate> update = new ArrayList<>();
        List<ConnectionChangeSummary> added = new ArrayList<>();
        List<ConnectionChangeSummary> changed = new ArrayList<>();
        Map<ConnectionKey, Boolean> matched = new LinkedHashMap<>();
        for (ConnectionEntry e : doc.connections()) {
            Connection existing = baseline.get(e.key());
            if (existing == null) {
                add.add(e);
                added.add(new ConnectionChangeSummary(
                        null,
                        e.name(),
                        e.source(),
                        e.target(),
                        null,
                        kind(e.kind()),
                        null,
                        e.description(),
                        null,
                        e.flow(),
                        null,
                        e.endpoint(),
                        null,
                        e.step()));
                continue;
            }
            matched.put(e.key(), true);
            String flowBefore = existing.getFlowId() == null ? null : flowNameById.get(existing.getFlowId());
            boolean differs = !existing.getName().equals(e.name())
                    || existing.getKind() != e.kind()
                    || !Objects.equals(existing.getDescription(), e.description())
                    || !Objects.equals(existing.getEndpoint(), e.endpoint())
                    || !Objects.equals(
                            flowBefore == null ? null : Names.norm(flowBefore),
                            e.flow() == null ? null : Names.norm(e.flow()))
                    // an absent step keeps the current one (spec 004 data-model.md §5)
                    || (e.step() != null && !Objects.equals(existing.getStep(), e.step()));
            if (differs) {
                update.add(new ConnectionUpdate(existing, e));
                changed.add(new ConnectionChangeSummary(
                        existing.getId(),
                        e.name(),
                        existing.getSourceName(),
                        existing.getTargetName(),
                        kind(existing.getKind()),
                        kind(e.kind()),
                        existing.getDescription(),
                        e.description(),
                        flowBefore,
                        e.flow(),
                        existing.getEndpoint(),
                        e.endpoint(),
                        existing.getStep(),
                        e.step() != null ? e.step() : e.flow() == null ? null : existing.getStep()));
            }
        }
        List<Connection> remove = new ArrayList<>();
        List<ConnectionChangeSummary> removed = new ArrayList<>();
        for (Map.Entry<ConnectionKey, Connection> b : baseline.entrySet()) {
            if (!matched.containsKey(b.getKey())) {
                Connection f = b.getValue();
                remove.add(f);
                removed.add(new ConnectionChangeSummary(
                        f.getId(),
                        f.getName(),
                        f.getSourceName(),
                        f.getTargetName(),
                        kind(f.getKind()),
                        null,
                        f.getDescription(),
                        null,
                        f.getFlowId() == null ? null : flowNameById.get(f.getFlowId()),
                        null,
                        f.getEndpoint(),
                        null,
                        f.getStep(),
                        null));
            }
        }
        boolean unchanged =
                toCreate.isEmpty() && msChanged.isEmpty() && added.isEmpty() && changed.isEmpty() && removed.isEmpty();
        ConnectionsDocumentChanges changes = new ConnectionsDocumentChanges(
                List.copyOf(toCreate.values()),
                List.copyOf(msChanged),
                List.copyOf(added),
                List.copyOf(changed),
                List.copyOf(removed),
                List.copyOf(flowsAdded),
                List.copyOf(flowsChanged),
                unchanged);
        return new Plan(
                List.copyOf(upserts),
                List.copyOf(toCreate.values()),
                Map.copyOf(createEntries),
                List.copyOf(describe),
                List.copyOf(add),
                List.copyOf(update),
                List.copyOf(remove),
                changes);
    }

    private static String kind(ConnectionKind kind) {
        return kind == null ? null : kind.documentValue();
    }
}
