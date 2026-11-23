package com.refinements.visualizer.connection.application;

import com.refinements.visualizer.connection.application.port.in.ConnectionsDocumentUseCases;
import com.refinements.visualizer.connection.application.port.out.ConnectionStore;
import com.refinements.visualizer.connection.application.port.out.DocumentCodec;
import com.refinements.visualizer.connection.application.port.out.FlowStore;
import com.refinements.visualizer.connection.application.port.out.MicroserviceDirectory;
import com.refinements.visualizer.connection.application.port.out.Revision;
import com.refinements.visualizer.connection.domain.Connection;
import com.refinements.visualizer.connection.domain.Flow;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentDiff;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentExport;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentModel;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentModel.ConnectionEntry;
import com.refinements.visualizer.connection.domain.document.ConnectionsDocumentModel.MicroserviceEntry;
import com.refinements.visualizer.microservice.MicroserviceView;
import com.refinements.visualizer.shared.Names;
import com.refinements.visualizer.shared.VisualizerProperties;
import com.refinements.visualizer.shared.errors.PayloadTooLargeException;
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
class ConnectionsDocumentService implements ConnectionsDocumentUseCases {

    private final MicroserviceDirectory microservices;
    private final ConnectionStore connections;
    private final FlowStore flows;
    private final FlowService flowService;
    private final DocumentCodec codec;
    private final Revision revision;
    private final Clock clock;
    private final long maxBytes;

    ConnectionsDocumentService(
            MicroserviceDirectory microservices,
            ConnectionStore connections,
            FlowStore flows,
            FlowService flowService,
            DocumentCodec codec,
            Revision revision,
            Clock clock,
            VisualizerProperties props) {
        this.microservices = microservices;
        this.connections = connections;
        this.flows = flows;
        this.flowService = flowService;
        this.codec = codec;
        this.revision = revision;
        this.clock = clock;
        this.maxBytes = props.connectionsDocument().maxBytes();
    }

    @Override
    @Transactional(readOnly = true)
    public Exported export() {
        ConnectionsDocumentExport.Exported e =
                ConnectionsDocumentExport.of(microservices.findAll(), flows.findAll(), connections.findAll());
        return new Exported(
                e.document(), codec.write(e.document()), revision.current(), e.excludedDanglingConnections());
    }

    @Override
    @Transactional(readOnly = true)
    public Outcome preview(String text) {
        ConnectionsDocumentModel doc = read(text);
        ConnectionsDocumentDiff.Plan plan =
                ConnectionsDocumentDiff.diff(doc, microservices.findAll(), flows.findAll(), connections.findAll());
        return new Outcome(plan.changes(), revision.current());
    }

    @Override
    @Transactional
    public Outcome apply(String text, Long baseRevision) {
        ConnectionsDocumentModel doc = read(text);
        revision.check(baseRevision);
        ConnectionsDocumentDiff.Plan plan =
                ConnectionsDocumentDiff.diff(doc, microservices.findAll(), flows.findAll(), connections.findAll());
        if (plan.changes().unchanged()) {
            return new Outcome(plan.changes(), revision.current());
        }
        Instant now = clock.instant();

        for (ConnectionsDocumentDiff.FlowUpsert g : plan.flows()) {
            if (g.existing() == null) {
                flowService.createWithoutRevision(g.name(), g.color());
            } else {
                g.existing().recolor(g.color(), now);
                flows.save(g.existing());
            }
        }
        Map<String, UUID> flowIds = new HashMap<>();
        for (Flow g : flows.findAll()) {
            flowIds.put(g.getNameNormalized(), g.getId());
        }

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
        connections.deleteAll(
                plan.connectionsToRemove().stream().map(Connection::getId).toList());
        connections.flush();

        Map<String, MicroserviceView> byName = new HashMap<>();
        microservices.findAll().forEach(m -> byName.put(m.nameNormalized(), m));
        List<Connection> needStep = new ArrayList<>();
        for (ConnectionsDocumentDiff.ConnectionUpdate u : plan.connectionsToUpdate()) {
            // reload: reattaching above may have changed its ends since the diff read it
            Connection f = connections.findById(u.connection().getId()).orElseThrow();
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
            connections.save(f);
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
            connections.save(f);
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
            connections.save(c);
        }
        connections.flush();
        return new Outcome(plan.changes(), revision.bump());
    }

    private static UUID flowOf(Map<String, UUID> flowIds, ConnectionEntry e) {
        return e.flow() == null ? null : flowIds.get(Names.norm(e.flow()));
    }

    private ConnectionsDocumentModel read(String text) {
        if (text != null && text.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new PayloadTooLargeException(
                    "The connections document exceeds the " + (maxBytes / (1024 * 1024)) + " MB limit.");
        }
        return codec.read(text);
    }
}
