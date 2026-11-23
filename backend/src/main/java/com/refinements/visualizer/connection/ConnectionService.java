package com.refinements.visualizer.connection;

import com.refinements.visualizer.connection.dto.ConnectionCreate;
import com.refinements.visualizer.connection.dto.ConnectionMutation;
import com.refinements.visualizer.connection.dto.ConnectionPatch;
import com.refinements.visualizer.connection.dto.ReassignRequest;
import com.refinements.visualizer.microservice.MicroserviceApi;
import com.refinements.visualizer.microservice.MicroserviceView;
import com.refinements.visualizer.shared.Names;
import com.refinements.visualizer.shared.errors.BadRequestException;
import com.refinements.visualizer.shared.errors.ConflictException;
import com.refinements.visualizer.shared.errors.NotFoundException;
import com.refinements.visualizer.workspace.WorkspaceRevision;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
public class ConnectionService implements ConnectionApi {

    private final ConnectionRepository connections;
    private final FlowRepository flows;
    private final MicroserviceApi microservices;
    private final WorkspaceRevision revision;
    private final Clock clock;

    public ConnectionService(
            ConnectionRepository connections,
            FlowRepository flows,
            MicroserviceApi microservices,
            WorkspaceRevision revision,
            Clock clock) {
        this.connections = connections;
        this.flows = flows;
        this.microservices = microservices;
        this.revision = revision;
        this.clock = clock;
    }

    @Transactional
    public ConnectionMutation create(ConnectionCreate request) {
        MicroserviceView source = microservice(request.sourceId());
        MicroserviceView target = microservice(request.targetId());
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
        return new ConnectionMutation(ConnectionDto.from(f), revision.bump());
    }

    private java.util.UUID checkFlow(java.util.UUID flowId) {
        if (flowId != null && !flows.existsById(flowId)) {
            throw new NotFoundException("No flow " + flowId + ".");
        }
        return flowId;
    }

    @Transactional
    public ConnectionMutation update(UUID id, ConnectionPatch patch) {
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
            f.setFlow(checkFlow(patch.flowId() == null ? null : java.util.UUID.fromString(patch.flowId())), now);
        }
        if (patch.stepPresent()) {
            f.setStep(stepFor(f, ConnectionSteps.check(patch.step())), now);
        } else if (!java.util.Objects.equals(flowBefore, f.getFlowId())) {
            // joining another flow appends; leaving the flow clears the step (FR-002)
            f.setStep(stepFor(f, null), now);
        }
        connections.saveAndFlush(f);
        return new ConnectionMutation(ConnectionDto.from(f), revision.bump());
    }

    /** The step to store: none without a flow, the given one, or appended (FR-002). */
    private Integer stepFor(Connection f, Integer requested) {
        if (f.getFlowId() == null) {
            if (requested != null) {
                throw new BadRequestException("A step needs a flow; choose the connection's flow first.");
            }
            return null;
        }
        return requested != null ? requested : ConnectionSteps.next(connections, f.getFlowId(), f.getId());
    }

    @Transactional
    public void delete(UUID id) {
        connections.delete(find(id));
        connections.flush();
        revision.bump();
    }

    /** Points a dangling end at an existing microservice (FR-027). */
    @Transactional
    public ConnectionMutation reassign(UUID id, ReassignRequest request) {
        if (request.sourceId() == null && request.targetId() == null) {
            throw new BadRequestException("Give sourceId, targetId or both.");
        }
        Connection f = find(id);
        MicroserviceView source = request.sourceId() == null ? null : microservice(request.sourceId());
        MicroserviceView target = request.targetId() == null ? null : microservice(request.targetId());
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
        return new ConnectionMutation(ConnectionDto.from(f), revision.bump());
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

    private MicroserviceView microservice(UUID id) {
        return microservices.find(id);
    }

    /** Blank → null; otherwise the normalized {@code METHOD /path} (data-model.md §3c), or 400. */
    static String checkEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        String normalized = ConnectionEndpoints.normalize(endpoint);
        if (normalized == null) {
            throw new BadRequestException("endpoint must look like 'POST /invoices' with a valid HTTP method.");
        }
        return normalized;
    }

    static String checkName(String name) {
        String clean = Names.clean(name);
        if (clean.isEmpty()) {
            throw new BadRequestException("A connection needs a name.");
        }
        if (clean.length() > Names.MAX_NAME) {
            throw new BadRequestException("Connection names are at most " + Names.MAX_NAME + " characters.");
        }
        return clean;
    }

    static void checkDescription(String description) {
        if (description != null && description.length() > Names.MAX_DESCRIPTION) {
            throw new BadRequestException("Descriptions are at most " + Names.MAX_DESCRIPTION + " characters.");
        }
    }

    private static ConflictException selfConnection() {
        return new ConflictException(
                "A connection must go to a different microservice.", ConflictException.SELF_CONNECTION);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConnectionView> findAll() {
        return connections.findAll().stream().map(ConnectionView::from).toList();
    }
}
