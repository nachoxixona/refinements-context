package com.refinements.visualizer.microservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.refinements.visualizer.microservice.application.port.in.MicroserviceUseCases.Deletion;
import com.refinements.visualizer.microservice.application.port.in.MicroserviceUseCases.MicroserviceChanges;
import com.refinements.visualizer.microservice.application.port.in.MicroserviceUseCases.Mutation;
import com.refinements.visualizer.microservice.application.port.in.MicroserviceUseCases.NewMicroservice;
import com.refinements.visualizer.microservice.application.port.out.MicroserviceStore;
import com.refinements.visualizer.microservice.domain.Microservice;
import com.refinements.visualizer.microservice.spi.ConnectionEndsPort;
import com.refinements.visualizer.shared.errors.ConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The microservice use cases with in-memory ports: no Spring, no database (specs/005 SC-005). */
class MicroserviceUseCasesTest {

    private final List<String> calls = new ArrayList<>();
    private final InMemoryMicroservices store = new InMemoryMicroservices();
    private final UUID danglingConnection = UUID.randomUUID();
    private long revision = 10;

    private final MicroserviceService service = new MicroserviceService(
            store,
            cardId -> true,
            new RecordingConnectionEnds(),
            () -> {
                calls.add("bump");
                return ++revision;
            },
            Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC));

    private Mutation create(String name) {
        return service.create(new NewMicroservice(name, null, null, null, null));
    }

    @Test
    void deleteDetachesConnectionEndsBeforeDeletingThenBumps() {
        UUID id = create("Orders").microservice().id();
        calls.clear();

        Deletion d = service.delete(id);

        assertThat(calls).containsExactly("detach " + id, "delete " + id, "bump");
        assertThat(d.danglingConnectionIds()).containsExactly(danglingConnection);
        assertThat(d.revision()).isEqualTo(12);
    }

    @Test
    void renameUpdatesConnectionEndsSavesThenReattachesThenBumps() {
        UUID id = create("Orders").microservice().id();
        calls.clear();

        service.update(id, new MicroserviceChanges("Ordering", null, false, null, false, null, false, null, false));

        assertThat(calls).containsExactly("renamed " + id + " Ordering", "save Ordering", "reattach ordering", "bump");
    }

    @Test
    void namesMustBeUniqueIgnoringCaseAndSpacing() {
        create("Orders");
        assertThatThrownBy(() -> create("  orders ")).isInstanceOf(ConflictException.class);
    }

    // --- in-memory ports ----------------------------------------------------------------------------------

    final class InMemoryMicroservices implements MicroserviceStore {
        final Map<UUID, Microservice> rows = new LinkedHashMap<>();

        public Optional<Microservice> findById(UUID id) {
            return Optional.ofNullable(rows.get(id));
        }

        public Optional<Microservice> findByNameNormalized(String n) {
            return rows.values().stream()
                    .filter(m -> m.getNameNormalized().equals(n))
                    .findFirst();
        }

        public Optional<Microservice> findBySpecCardId(UUID cardId) {
            return rows.values().stream()
                    .filter(m -> cardId.equals(m.getSpecCardId()))
                    .findFirst();
        }

        public List<Microservice> findAll() {
            return List.copyOf(rows.values());
        }

        public List<Microservice> findAllOrdered() {
            return rows.values().stream()
                    .sorted(Comparator.comparing(Microservice::getNameNormalized))
                    .toList();
        }

        public void save(Microservice m) {
            calls.add("save " + m.getName());
            rows.put(m.getId(), m);
        }

        public void delete(UUID id) {
            calls.add("delete " + id);
            rows.remove(id);
        }
    }

    final class RecordingConnectionEnds implements ConnectionEndsPort {
        public List<UUID> detach(UUID id) {
            calls.add("detach " + id);
            return List.of(danglingConnection);
        }

        public void renamed(UUID id, String newName) {
            calls.add("renamed " + id + " " + newName);
        }

        public List<UUID> reattach(UUID id, String name, String nameNormalized) {
            calls.add("reattach " + nameNormalized);
            return List.of();
        }
    }
}
