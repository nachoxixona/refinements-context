package com.refinements.visualizer.connection.document;

import com.refinements.visualizer.connection.Connection;
import com.refinements.visualizer.connection.ConnectionRepository;
import com.refinements.visualizer.connection.FlowRepository;
import com.refinements.visualizer.connection.FlowService;
import com.refinements.visualizer.connection.document.ConnectionsDocumentModel.ConnectionEntry;
import com.refinements.visualizer.connection.document.ConnectionsDocumentModel.MicroserviceEntry;
import com.refinements.visualizer.microservice.MicroserviceApi;
import com.refinements.visualizer.microservice.MicroserviceView;
import com.refinements.visualizer.shared.Names;
import com.refinements.visualizer.shared.VisualizerProperties;
import com.refinements.visualizer.shared.errors.PayloadTooLargeException;
import com.refinements.visualizer.workspace.WorkspaceRevision;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Export, preview and replace-apply of the connections document (FR-020 – FR-025, research R4, R5, R7). */
@Service
public class ConnectionsDocumentService {

    private final MicroserviceApi microservices;
    private final ConnectionRepository connections;
    private final FlowRepository flows;
    private final FlowService flowService;
    private final ConnectionsDocumentReader reader;
    private final ConnectionsDocumentWriter writer;
    private final WorkspaceRevision revision;
    private final Clock clock;
    private final long maxBytes;

    public ConnectionsDocumentService(
            MicroserviceApi microservices,
            ConnectionRepository connections,
            FlowRepository flows,
            FlowService flowService,
            ConnectionsDocumentReader reader,
            ConnectionsDocumentWriter writer,
            WorkspaceRevision revision,
            Clock clock,
            VisualizerProperties props) {
        this.microservices = microservices;
        this.connections = connections;
        this.flows = flows;
        this.flowService = flowService;
        this.reader = reader;
        this.writer = writer;
        this.revision = revision;
        this.clock = clock;
        this.maxBytes = props.connectionsDocument().maxBytes();
    }

    @Transactional(readOnly = true)
    public ConnectionsDocumentApi.Export export() {
        ConnectionsDocumentWriter.Export e =
                writer.export(microservices.findAll(), flows.findAll(), connections.findAll());
        return new ConnectionsDocumentApi.Export(
                e.document(), e.text(), revision.current(), e.excludedDanglingConnections());
    }

    @Transactional(readOnly = true)
    public ConnectionsDocumentApi.Preview preview(String text) {
        ConnectionsDocumentModel doc = read(text);
        ConnectionsDocumentDiff.Plan plan =
                ConnectionsDocumentDiff.diff(doc, microservices.findAll(), flows.findAll(), connections.findAll());
        return new ConnectionsDocumentApi.Preview(plan.changes(), revision.current());
    }

    /** Validates, re-diffs and applies in one transaction; a stale {@code baseRevision} → 409 (research R5). */
    @Transactional
    public ConnectionsDocumentApi.Applied apply(String text, Long baseRevision) {
        ConnectionsDocumentModel doc = read(text);
        revision.check(baseRevision);
        ConnectionsDocumentDiff.Plan plan =
                ConnectionsDocumentDiff.diff(doc, microservices.findAll(), flows.findAll(), connections.findAll());
        if (plan.changes().unchanged()) {
            return new ConnectionsDocumentApi.Applied(plan.changes(), revision.current());
        }
        Instant now = clock.instant();

        for (ConnectionsDocumentDiff.FlowUpsert g : plan.flows()) {
            if (g.existing() == null) {
                flowService.createInternal(g.name(), g.color());
            } else {
                g.existing().recolor(g.color(), now);
            }
        }
        Map<String, UUID> flowIds = new HashMap<>();
        flows.findAll().forEach(g -> flowIds.put(g.getNameNormalized(), g.getId()));

        for (String name : plan.microservicesToCreate()) {
            MicroserviceEntry entry = plan.createEntries().get(Names.norm(name));
            MicroserviceView m = microservices.createWithoutRevision(name, entry == null ? null : entry.description());
            if (entry != null) {
                microservices.ownWithoutRevision(m.id(), entry.team(), entry.repository(), entry.docs());
            }
            microservices.reattach(m.id());
        }
        for (ConnectionsDocumentDiff.Describe d : plan.describe()) {
            microservices.describeWithoutRevision(d.microservice().id(), d.description());
            microservices.ownWithoutRevision(d.microservice().id(), d.team(), d.repository(), d.docs());
        }
        connections.deleteAll(plan.connectionsToRemove());
        connections.flush();

        Map<String, MicroserviceView> byName = new HashMap<>();
        microservices.findAll().forEach(m -> byName.put(m.nameNormalized(), m));
        List<Connection> needStep = new ArrayList<>();
        for (ConnectionsDocumentDiff.ConnectionUpdate u : plan.connectionsToUpdate()) {
            Connection f = u.connection();
            UUID flowBefore = f.getFlowId();
            f.rename(u.entry().name(), now);
            f.change(u.entry().kind(), u.entry().description(), now);
            f.setFlow(flowOf(flowIds, u.entry()), now);
            f.setEndpoint(u.entry().endpoint(), now);
            if (f.getFlowId() == null) {
                f.setStep(null, now);
            } else if (u.entry().step() != null) {
                f.setStep(u.entry().step(), now);
            } else if (!Objects.equals(flowBefore, f.getFlowId())) {
                f.setStep(null, now);
                needStep.add(f);
            }
        }
        for (ConnectionEntry e : plan.connectionsToAdd()) {
            MicroserviceView source = byName.get(Names.norm(e.source()));
            MicroserviceView target = byName.get(Names.norm(e.target()));
            Connection f = new Connection(e.name(), e.kind(), e.description(), now);
            f.setSource(source.id(), source.name(), now);
            f.setTarget(target.id(), target.name(), now);
            f.setFlow(flowOf(flowIds, e), now);
            f.setEndpoint(e.endpoint(), now);
            f.setStep(f.getFlowId() == null ? null : e.step(), now);
            // save() merges an entity with an assigned id: keep the managed copy for the step pass below
            f = connections.save(f);
            if (f.getFlowId() != null && e.step() == null) {
                needStep.add(f);
            }
        }
        connections.flush();
        // connections joining a flow without a step are appended, in document order (spec 004 FR-002)
        Map<UUID, Integer> max = new HashMap<>();
        for (Connection c : connections.findAll()) {
            if (c.getFlowId() != null && c.getStep() != null) {
                max.merge(c.getFlowId(), c.getStep(), Math::max);
            }
        }
        for (Connection c : needStep) {
            int next = max.getOrDefault(c.getFlowId(), 0) + 1;
            max.put(c.getFlowId(), next);
            c.setStep(next, now);
        }
        connections.flush();
        return new ConnectionsDocumentApi.Applied(plan.changes(), revision.bump());
    }

    private static UUID flowOf(Map<String, UUID> flowIds, ConnectionEntry e) {
        return e.flow() == null ? null : flowIds.get(Names.norm(e.flow()));
    }

    private ConnectionsDocumentModel read(String text) {
        if (text != null && text.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new PayloadTooLargeException(
                    "The connections document exceeds the " + (maxBytes / (1024 * 1024)) + " MB limit.");
        }
        return reader.read(text);
    }
}
