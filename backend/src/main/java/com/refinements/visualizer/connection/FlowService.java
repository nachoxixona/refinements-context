package com.refinements.visualizer.connection;

import com.refinements.visualizer.connection.dto.FlowDto;
import com.refinements.visualizer.connection.dto.FlowMutation;
import com.refinements.visualizer.connection.dto.FlowPatch;
import com.refinements.visualizer.shared.Names;
import com.refinements.visualizer.shared.errors.BadRequestException;
import com.refinements.visualizer.shared.errors.ConflictException;
import com.refinements.visualizer.shared.errors.NotFoundException;
import com.refinements.visualizer.workspace.WorkspaceRevision;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Flows: create, rename, recolor, delete (FR-049). Deleting a flow ungroups its connections (FR-050). */
@Service
public class FlowService {

    private final FlowRepository flows;
    private final ConnectionRepository connections;
    private final WorkspaceRevision revision;
    private final Clock clock;

    public FlowService(
            FlowRepository flows, ConnectionRepository connections, WorkspaceRevision revision, Clock clock) {
        this.flows = flows;
        this.connections = connections;
        this.revision = revision;
        this.clock = clock;
    }

    /**
     * Sets the steps of a flow's connections in one go (spec 004 FR-004): {@code steps} must list every connection
     * of the flow exactly once, each with a step ≥ 1.
     */
    @Transactional
    public long setSteps(UUID id, java.util.List<com.refinements.visualizer.connection.dto.StepsWrite.Step> steps) {
        find(id);
        java.util.Map<UUID, Connection> members = new java.util.HashMap<>();
        connections.findByFlowId(id).forEach(c -> members.put(c.getId(), c));
        java.util.Set<UUID> listed = new java.util.HashSet<>();
        for (var s : steps) {
            if (s.connectionId() == null || !members.containsKey(s.connectionId())) {
                throw new BadRequestException("Connection " + s.connectionId() + " is not in this flow.");
            }
            if (!listed.add(s.connectionId())) {
                throw new BadRequestException("Connection " + s.connectionId() + " is listed twice.");
            }
            ConnectionSteps.check(s.step());
            if (s.step() == null) {
                throw new BadRequestException("Every listed connection needs a step.");
            }
        }
        if (listed.size() != members.size()) {
            throw new BadRequestException("List every connection of the flow; " + members.size() + " expected.");
        }
        java.time.Instant now = clock.instant();
        for (var s : steps) {
            members.get(s.connectionId()).setStep(s.step(), now);
        }
        connections.flush();
        return revision.bump();
    }

    @Transactional
    public FlowMutation create(String name, FlowColor color) {
        Flow g = createInternal(name, color);
        return new FlowMutation(FlowDto.from(g), revision.bump());
    }

    /** Creates a flow without bumping the revision (also used by the connections document). */
    public Flow createInternal(String name, FlowColor color) {
        String clean = checkName(name);
        if (color == null) {
            throw new BadRequestException("A flow needs a color.");
        }
        if (flows.findByNameNormalized(Names.norm(clean)).isPresent()) {
            throw duplicate(clean);
        }
        return flows.saveAndFlush(new Flow(clean, color, clock.instant()));
    }

    @Transactional
    public FlowMutation update(UUID id, FlowPatch patch) {
        Flow g = find(id);
        if (patch.name() != null) {
            String clean = checkName(patch.name());
            Optional<Flow> clash = flows.findByNameNormalized(Names.norm(clean));
            if (clash.isPresent() && !clash.get().getId().equals(id)) {
                throw duplicate(clean);
            }
            g.rename(clean, clock.instant());
        }
        if (patch.color() != null) {
            g.recolor(patch.color(), clock.instant());
        }
        flows.saveAndFlush(g);
        return new FlowMutation(FlowDto.from(g), revision.bump());
    }

    @Transactional
    public void delete(UUID id) {
        Flow flow = find(id);
        java.time.Instant now = clock.instant();
        // the FK ungroups the connections; their steps go with the flow (spec 004 FR-002)
        for (Connection c : connections.findByFlowId(id)) {
            c.setFlow(null, now);
            c.setStep(null, now);
        }
        connections.flush();
        flows.delete(flow);
        flows.flush();
        revision.bump();
    }

    public Flow find(UUID id) {
        return flows.findById(id).orElseThrow(() -> new NotFoundException("No flow " + id + "."));
    }

    private static String checkName(String name) {
        String clean = Names.clean(name);
        if (clean.isEmpty()) {
            throw new BadRequestException("A flow needs a name.");
        }
        if (clean.length() > Names.MAX_NAME) {
            throw new BadRequestException("Flow names are at most " + Names.MAX_NAME + " characters.");
        }
        return clean;
    }

    private static ConflictException duplicate(String name) {
        return new ConflictException("A flow named '" + name + "' already exists.", ConflictException.DUPLICATE_NAME);
    }
}
