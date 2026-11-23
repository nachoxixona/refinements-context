package com.refinements.visualizer.connection.application;

import static com.refinements.visualizer.connection.domain.ConnectionRules.checkDescription;
import static com.refinements.visualizer.connection.domain.ConnectionRules.checkEndpoint;
import static com.refinements.visualizer.connection.domain.ConnectionRules.checkName;
import static com.refinements.visualizer.connection.domain.ConnectionRules.selfConnection;

import com.refinements.visualizer.connection.ConnectionApi;
import com.refinements.visualizer.connection.ConnectionKey;
import com.refinements.visualizer.connection.ConnectionKind;
import com.refinements.visualizer.connection.KeyedConnection;
import com.refinements.visualizer.connection.application.port.in.ConnectionUseCases;
import com.refinements.visualizer.connection.application.port.out.ConnectionStore;
import com.refinements.visualizer.connection.application.port.out.FlowStore;
import com.refinements.visualizer.connection.application.port.out.MicroserviceDirectory;
import com.refinements.visualizer.connection.application.port.out.Revision;
import com.refinements.visualizer.connection.domain.Connection;
import com.refinements.visualizer.connection.domain.ConnectionSteps;
import com.refinements.visualizer.microservice.MicroserviceView;
import com.refinements.visualizer.shared.Names;
import com.refinements.visualizer.shared.errors.BadRequestException;
import com.refinements.visualizer.shared.errors.ConflictException;
import com.refinements.visualizer.shared.errors.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connections drawn and edited on the canvas (FR-017, FR-018a, FR-024, FR-027), with the rules of data-model.md §3
 * for Connection.name, Connection.kind, Connection.description and the (source, target, name) key. Every write bumps the
 * workspace revision.
 */
@Service
class ConnectionService implements ConnectionApi, ConnectionUseCases {

    private final ConnectionStore connections;
    private final FlowStore flows;
    private final MicroserviceDirectory microservices;
    private final Revision revision;
    private final Clock clock;

    ConnectionService(
            ConnectionStore connections,
            FlowStore flows,
            MicroserviceDirectory microservices,
            Revision revision,
            Clock clock) {
        this.connections = connections;
        this.flows = flows;
        this.microservices = microservices;
        this.revision = revision;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Mutation create(NewConnection request) {
        MicroserviceView source = microservices.find(request.sourceId());
        MicroserviceView target = microservices.find(request.targetId());
        String name = checkName(request.name());
        checkDescription(request.description());
        if (source.id().equals(target.id())) {
            throw selfConnection();
        }
        ConnectionKey key = ConnectionKey.of(source.name(), target.name(), name);
        checkUnique(key, null);
        Instant now = clock.instant();
        Connection f = new Connection(name, request.kind(), request.description(), now);
        f.setSource(source.id(), source.name(), now);
        f.setTarget(target.id(), target.name(), now);
        f.setFlow(checkFlow(request.flowId()), now);
        f.setStep(stepFor(f, ConnectionSteps.check(request.step())), now);
        f.setEndpoint(checkEndpoint(request.endpoint()), now);
        connections.saveAndFlush(f);
        return new Mutation(f.view(), revision.bump());
    }

    private UUID checkFlow(UUID flowId) {
        if (flowId != null && !flows.existsById(flowId)) {
            throw new NotFoundException("No flow " + flowId + ".");
        }
        return flowId;
    }

    @Override
    @Transactional
    public Mutation update(UUID id, ConnectionChanges patch) {
        Connection f = find(id);
        Instant now = clock.instant();
        if (patch.name() != null) {
            String name = checkName(patch.name());
            checkUnique(ConnectionKey.of(f.getSourceName(), f.getTargetName(), name), id);
            f.rename(name, now);
        }
        ConnectionKind kind = patch.kindPresent() ? patch.kind() : f.getKind();
        String description = patch.descriptionPresent() ? patch.description() : f.getDescription();
        checkDescription(description);
        f.change(kind, description, now);
        if (patch.endpointPresent()) {
            f.setEndpoint(checkEndpoint(patch.endpoint()), now);
        }
        UUID flowBefore = f.getFlowId();
        if (patch.flowPresent()) {
            f.setFlow(checkFlow(patch.flowId()), now);
        }
        if (patch.stepPresent()) {
            f.setStep(stepFor(f, ConnectionSteps.check(patch.step())), now);
        } else if (!Objects.equals(flowBefore, f.getFlowId())) {
            // joining another flow appends; leaving the flow clears the step (FR-002)
            f.setStep(stepFor(f, null), now);
        }
        connections.saveAndFlush(f);
        return new Mutation(f.view(), revision.bump());
    }

    /** The step to store: none without a flow, the given one, or appended (FR-002). */
    private Integer stepFor(Connection f, Integer requested) {
        if (f.getFlowId() == null) {
            if (requested != null) {
                throw new BadRequestException("A step needs a flow; choose the connection's flow first.");
            }
            return null;
        }
        return requested != null ? requested : ConnectionSteps.next(connections.findByFlowId(f.getFlowId()), f.getId());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        connections.delete(find(id).getId());
        connections.flush();
        revision.bump();
    }

    @Override
    @Transactional
    public Mutation reassign(UUID id, UUID sourceId, UUID targetId) {
        if (sourceId == null && targetId == null) {
            throw new BadRequestException("Give sourceId, targetId or both.");
        }
        Connection f = find(id);
        MicroserviceView source = sourceId == null ? null : microservices.find(sourceId);
        MicroserviceView target = targetId == null ? null : microservices.find(targetId);
        String sourceName = source == null ? f.getSourceName() : source.name();
        String targetName = target == null ? f.getTargetName() : target.name();
        if (Names.norm(sourceName).equals(Names.norm(targetName))) {
            throw selfConnection();
        }
        checkUnique(ConnectionKey.of(sourceName, targetName, f.getName()), id);
        Instant now = clock.instant();
        if (source != null) {
            f.setSource(source.id(), source.name(), now);
        }
        if (target != null) {
            f.setTarget(target.id(), target.name(), now);
        }
        connections.saveAndFlush(f);
        return new Mutation(f.view(), revision.bump());
    }

    private void checkUnique(ConnectionKey key, UUID except) {
        Optional<Connection> clash = connections.findByKey(key);
        if (clash.isPresent() && !clash.get().getId().equals(except)) {
            throw new ConflictException(
                    "A connection named '" + clash.get().getName() + "' from "
                            + clash.get().getSourceName() + " to " + clash.get().getTargetName() + " already exists.",
                    ConflictException.DUPLICATE_CONNECTION);
        }
    }

    private Connection find(UUID id) {
        return connections.findById(id).orElseThrow(() -> new NotFoundException("No connection " + id + "."));
    }

    @Override
    @Transactional(readOnly = true)
    public List<KeyedConnection> findAll() {
        return connections.findAll().stream()
                .map(f -> new KeyedConnection(f.view(), f.key()))
                .toList();
    }
}
