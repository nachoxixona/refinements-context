package com.refinements.visualizer.connection.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.refinements.visualizer.connection.ConnectionKey;
import com.refinements.visualizer.connection.ConnectionKind;
import com.refinements.visualizer.connection.FlowColor;
import com.refinements.visualizer.connection.application.port.in.ConnectionUseCases.ConnectionChanges;
import com.refinements.visualizer.connection.application.port.in.ConnectionUseCases.Mutation;
import com.refinements.visualizer.connection.application.port.in.ConnectionUseCases.NewConnection;
import com.refinements.visualizer.connection.application.port.out.ConnectionStore;
import com.refinements.visualizer.connection.application.port.out.FlowStore;
import com.refinements.visualizer.connection.application.port.out.MicroserviceDirectory;
import com.refinements.visualizer.connection.application.port.out.Revision;
import com.refinements.visualizer.connection.domain.Connection;
import com.refinements.visualizer.connection.domain.Flow;
import com.refinements.visualizer.microservice.MicroserviceView;
import com.refinements.visualizer.shared.Names;
import com.refinements.visualizer.shared.errors.ConflictException;
import com.refinements.visualizer.shared.errors.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Connection use cases on in-memory stand-ins for every outbound port: no Spring, no database (SC-005). */
class ConnectionUseCasesTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC);
    private final InMemoryConnections connections = new InMemoryConnections();
    private final InMemoryFlows flows = new InMemoryFlows();
    private final Directory microservices = new Directory();
    private final Counter revision = new Counter();
    private final ConnectionService service = new ConnectionService(connections, flows, microservices, revision, clock);
    private final FlowService flowService = new FlowService(flows, connections, revision, clock);
    private final ConnectionEndsService ends = new ConnectionEndsService(connections, clock);

    private final MicroserviceView orders = microservices.add("Orders");
    private final MicroserviceView billing = microservices.add("Billing");

    private Mutation create(String name, UUID flowId, Integer step) {
        return service.create(
                new NewConnection(orders.id(), billing.id(), name, ConnectionKind.REQUEST, null, flowId, null, step));
    }

    @Test
    void createsAndBumpsTheRevision() {
        Mutation m = create("Create invoice", null, null);
        assertThat(m.revision()).isEqualTo(1);
        assertThat(m.connection().sourceName()).isEqualTo("Orders");
        assertThat(connections.rows).containsKey(m.connection().id());
    }

    @Test
    void rejectsSelfConnectionsDuplicateKeysAndUnknownFlows() {
        assertThatThrownBy(() -> service.create(
                        new NewConnection(orders.id(), orders.id(), "Loop", null, null, null, null, null)))
                .isInstanceOf(ConflictException.class);
        create("Pay", null, null);
        assertThatThrownBy(() -> create(" pay ", null, null)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> create("Other", UUID.randomUUID(), null)).isInstanceOf(NotFoundException.class);
        assertThat(revision.value).isEqualTo(1);
    }

    @Test
    void joiningAFlowAppendsAfterTheHighestStep() {
        UUID flow = flowService.create("Checkout", FlowColor.BLUE).flow().id();
        assertThat(create("A", flow, 3).connection().step()).isEqualTo(3);
        Mutation b = create("B", null, null);
        Mutation moved = service.update(
                b.connection().id(),
                new ConnectionChanges(null, null, false, null, false, flow, true, null, false, null, false));
        assertThat(moved.connection().step()).isEqualTo(4);
        Mutation left = service.update(
                b.connection().id(),
                new ConnectionChanges(null, null, false, null, false, null, true, null, false, null, false));
        assertThat(left.connection().step()).isNull();
    }

    @Test
    void deletingAFlowUngroupsItsConnections() {
        UUID flow = flowService.create("Checkout", FlowColor.BLUE).flow().id();
        UUID a = create("A", flow, 1).connection().id();
        flowService.delete(flow);
        assertThat(connections.rows.get(a).getFlowId()).isNull();
        assertThat(connections.rows.get(a).getStep()).isNull();
        assertThat(flows.rows).isEmpty();
    }

    @Test
    void endsDetachThenReattachByName() {
        UUID id = create("Pay", null, null).connection().id();
        assertThat(ends.detach(billing.id())).containsExactly(id);
        assertThat(connections.rows.get(id).isDangling()).isTrue();
        assertThat(connections.rows.get(id).getTargetName()).isEqualTo("Billing");
        assertThat(connections.flushes).isEqualTo(2); // create's saveAndFlush, then detach

        UUID newBilling = UUID.randomUUID();
        assertThat(ends.reattach(newBilling, "Billing", "billing")).containsExactly(id);
        assertThat(connections.rows.get(id).getTargetId()).isEqualTo(newBilling);
        assertThat(connections.rows.get(id).isDangling()).isFalse();
    }

    // in-memory stand-ins; they store copies, as the database does

    static final class InMemoryConnections implements ConnectionStore {
        final Map<UUID, Connection> rows = new LinkedHashMap<>();
        int flushes;

        private static Connection copy(Connection c) {
            return Connection.restore(
                    c.getId(),
                    c.getName(),
                    c.getNameNormalized(),
                    c.getKind(),
                    c.getDescription(),
                    c.getFlowId(),
                    c.getStep(),
                    c.getEndpoint(),
                    c.getSourceId(),
                    c.getTargetId(),
                    c.getSourceName(),
                    c.getTargetName(),
                    c.getSourceNameNormalized(),
                    c.getTargetNameNormalized(),
                    c.getCreatedAt(),
                    c.getUpdatedAt());
        }

        private List<Connection> where(java.util.function.Predicate<Connection> p) {
            return rows.values().stream()
                    .filter(p)
                    .map(InMemoryConnections::copy)
                    .toList();
        }

        @Override
        public Optional<Connection> findById(UUID id) {
            return Optional.ofNullable(rows.get(id)).map(InMemoryConnections::copy);
        }

        @Override
        public List<Connection> findAll() {
            return where(c -> true);
        }

        @Override
        public List<Connection> findByFlowId(UUID flowId) {
            return where(c -> flowId.equals(c.getFlowId()));
        }

        @Override
        public List<Connection> findAttachedTo(UUID id) {
            return where(c -> id.equals(c.getSourceId()) || id.equals(c.getTargetId()));
        }

        @Override
        public List<Connection> findDanglingByName(String n) {
            return where(c -> (c.getSourceId() == null
                            && c.getSourceNameNormalized().equals(n))
                    || (c.getTargetId() == null && c.getTargetNameNormalized().equals(n)));
        }

        @Override
        public Optional<Connection> findByKey(ConnectionKey key) {
            return where(c -> c.key().equals(key)).stream().findFirst();
        }

        @Override
        public void save(Connection c) {
            rows.put(c.getId(), copy(c));
        }

        @Override
        public void saveAndFlush(Connection c) {
            save(c);
            flush();
        }

        @Override
        public void delete(UUID id) {
            rows.remove(id);
        }

        @Override
        public void deleteAll(Collection<UUID> ids) {
            ids.forEach(rows::remove);
        }

        @Override
        public void flush() {
            flushes++;
        }
    }

    static final class InMemoryFlows implements FlowStore {
        final Map<UUID, Flow> rows = new LinkedHashMap<>();

        @Override
        public Optional<Flow> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public boolean existsById(UUID id) {
            return rows.containsKey(id);
        }

        @Override
        public Optional<Flow> findByNameNormalized(String n) {
            return rows.values().stream()
                    .filter(f -> f.getNameNormalized().equals(n))
                    .findFirst();
        }

        @Override
        public List<Flow> findAll() {
            return List.copyOf(rows.values());
        }

        @Override
        public List<Flow> findAllOrdered() {
            return findAll();
        }

        @Override
        public void save(Flow flow) {
            rows.put(flow.getId(), flow);
        }

        @Override
        public void saveAndFlush(Flow flow) {
            save(flow);
        }

        @Override
        public void delete(UUID id) {
            rows.remove(id);
        }
    }

    static final class Directory implements MicroserviceDirectory {
        final Map<UUID, MicroserviceView> rows = new LinkedHashMap<>();

        MicroserviceView add(String name) {
            MicroserviceView m =
                    new MicroserviceView(UUID.randomUUID(), name, Names.norm(name), null, null, null, null, null);
            rows.put(m.id(), m);
            return m;
        }

        @Override
        public MicroserviceView find(UUID id) {
            MicroserviceView m = rows.get(id);
            if (m == null) {
                throw new NotFoundException("No microservice " + id + ".");
            }
            return m;
        }

        @Override
        public List<MicroserviceView> findAll() {
            return new ArrayList<>(rows.values());
        }

        @Override
        public MicroserviceView createWithoutRevision(String name, String description) {
            return add(name);
        }

        @Override
        public void describeWithoutRevision(UUID id, String description) {}

        @Override
        public void ownWithoutRevision(UUID id, String team, String repository, String docs) {}

        @Override
        public void reattach(UUID id) {}
    }

    static final class Counter implements Revision {
        long value;

        @Override
        public long current() {
            return value;
        }

        @Override
        public long bump() {
            return ++value;
        }

        @Override
        public void check(Long baseRevision) {}
    }
}
