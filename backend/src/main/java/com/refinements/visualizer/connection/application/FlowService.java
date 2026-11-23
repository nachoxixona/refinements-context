package com.refinements.visualizer.connection.application;

import static com.refinements.visualizer.connection.domain.ConnectionRules.checkFlowName;

import com.refinements.visualizer.connection.FlowApi;
import com.refinements.visualizer.connection.FlowColor;
import com.refinements.visualizer.connection.FlowView;
import com.refinements.visualizer.connection.application.port.in.FlowUseCases;
import com.refinements.visualizer.connection.application.port.out.ConnectionStore;
import com.refinements.visualizer.connection.application.port.out.FlowStore;
import com.refinements.visualizer.connection.application.port.out.Revision;
import com.refinements.visualizer.connection.domain.Connection;
import com.refinements.visualizer.connection.domain.ConnectionSteps;
import com.refinements.visualizer.connection.domain.Flow;
import com.refinements.visualizer.shared.Names;
import com.refinements.visualizer.shared.errors.BadRequestException;
import com.refinements.visualizer.shared.errors.ConflictException;
import com.refinements.visualizer.shared.errors.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Flows: create, rename, recolor, delete (FR-049). Deleting a flow ungroups its connections (FR-050). */
@Service
class FlowService implements FlowApi, FlowUseCases {

    private final FlowStore flows;
    private final ConnectionStore connections;
    private final Revision revision;
    private final Clock clock;

    FlowService(FlowStore flows, ConnectionStore connections, Revision revision, Clock clock) {
        this.flows = flows;
        this.connections = connections;
        this.revision = revision;
        this.clock = clock;
    }

    /**
     * Sets the steps of a flow's connections in one go (spec 004 FR-004): {@code steps} must list every connection
     * of the flow exactly once, each with a step ≥ 1.
     */
    @Override
    @Transactional
    public long setSteps(UUID id, List<StepAssignment> steps) {
        find(id);
        Map<UUID, Connection> members = new HashMap<>();
        connections.findByFlowId(id).forEach(c -> members.put(c.getId(), c));
        Set<UUID> listed = new HashSet<>();
        for (StepAssignment s : steps) {
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
        Instant now = clock.instant();
        for (StepAssignment s : steps) {
            Connection c = members.get(s.connectionId());
            c.setStep(s.step(), now);
            connections.save(c);
        }
        connections.flush();
        return revision.bump();
    }

    @Override
    @Transactional
    public Mutation create(String name, FlowColor color) {
        Flow g = createWithoutRevision(name, color);
        return new Mutation(g.view(), revision.bump());
    }

    /** Creates a flow without bumping the revision (also used by the connections document). */
    Flow createWithoutRevision(String name, FlowColor color) {
        String clean = checkFlowName(name);
        if (color == null) {
            throw new BadRequestException("A flow needs a color.");
        }
        if (flows.findByNameNormalized(Names.norm(clean)).isPresent()) {
            throw duplicate(clean);
        }
        Flow flow = new Flow(clean, color, clock.instant());
        flows.saveAndFlush(flow);
        return flow;
    }

    @Override
    @Transactional
    public Mutation update(UUID id, FlowChanges patch) {
        Flow g = find(id);
        if (patch.name() != null) {
            String clean = checkFlowName(patch.name());
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
        return new Mutation(g.view(), revision.bump());
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Flow flow = find(id);
        Instant now = clock.instant();
        // the FK ungroups the connections; their steps go with the flow (spec 004 FR-002)
        for (Connection c : connections.findByFlowId(id)) {
            c.setFlow(null, now);
            c.setStep(null, now);
            connections.save(c);
        }
        connections.flush();
        flows.delete(flow.getId());
        revision.bump();
    }

    private Flow find(UUID id) {
        return flows.findById(id).orElseThrow(() -> new NotFoundException("No flow " + id + "."));
    }

    private static ConflictException duplicate(String name) {
        return new ConflictException("A flow named '" + name + "' already exists.", ConflictException.DUPLICATE_NAME);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowView> findAllOrdered() {
        return flows.findAllOrdered().stream().map(Flow::view).toList();
    }
}
