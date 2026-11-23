package com.refinements.visualizer.connection.adapter.out.microservice;

import com.refinements.visualizer.connection.Connection;
import com.refinements.visualizer.connection.ConnectionRepository;
import com.refinements.visualizer.microservice.spi.ConnectionEndsPort;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The connection module's side of {@link ConnectionEndsPort} (research R4). Runs in the caller's transaction. */
@Component
class ConnectionEndsAdapter implements ConnectionEndsPort {

    private final ConnectionRepository connections;
    private final Clock clock;

    ConnectionEndsAdapter(ConnectionRepository connections, Clock clock) {
        this.connections = connections;
        this.clock = clock;
    }

    @Override
    public List<UUID> detach(UUID microserviceId) {
        Instant now = clock.instant();
        List<UUID> dangling = new ArrayList<>();
        for (Connection f : connections.findBySourceIdOrTargetId(microserviceId, microserviceId)) {
            // keep the names; null the ids explicitly so the in-memory entities match the FK SET NULL
            if (microserviceId.equals(f.getSourceId())) {
                f.setSource(null, f.getSourceName(), now);
            }
            if (microserviceId.equals(f.getTargetId())) {
                f.setTarget(null, f.getTargetName(), now);
            }
            dangling.add(f.getId());
        }
        connections.flush();
        return dangling;
    }

    @Override
    public void renamed(UUID microserviceId, String newName) {
        Instant now = clock.instant();
        for (Connection f : connections.findBySourceIdOrTargetId(microserviceId, microserviceId)) {
            if (microserviceId.equals(f.getSourceId())) {
                f.setSource(microserviceId, newName, now);
            }
            if (microserviceId.equals(f.getTargetId())) {
                f.setTarget(microserviceId, newName, now);
            }
        }
    }

    @Override
    public List<UUID> reattach(UUID microserviceId, String name, String nameNormalized) {
        Instant now = clock.instant();
        List<UUID> ids = new ArrayList<>();
        for (Connection f : connections.findDanglingByName(nameNormalized)) {
            if (f.getSourceId() == null && f.getSourceNameNormalized().equals(nameNormalized)) {
                f.setSource(microserviceId, name, now);
            }
            if (f.getTargetId() == null && f.getTargetNameNormalized().equals(nameNormalized)) {
                f.setTarget(microserviceId, name, now);
            }
            ids.add(f.getId());
        }
        return ids;
    }
}
