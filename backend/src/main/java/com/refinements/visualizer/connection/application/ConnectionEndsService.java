package com.refinements.visualizer.connection.application;

import com.refinements.visualizer.connection.application.port.in.ConnectionEnds;
import com.refinements.visualizer.connection.application.port.out.ConnectionStore;
import com.refinements.visualizer.connection.domain.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Connection ends following microservice changes (research R4). Depends on the store and the clock only, so the
 * microservice module can call it without a bean cycle (contracts/module-apis.md, "SPI implementations are inbound
 * adapters"). Runs in the caller's transaction.
 */
@Service
class ConnectionEndsService implements ConnectionEnds {

    private final ConnectionStore connections;
    private final Clock clock;

    ConnectionEndsService(ConnectionStore connections, Clock clock) {
        this.connections = connections;
        this.clock = clock;
    }

    @Override
    public List<UUID> detach(UUID microserviceId) {
        Instant now = clock.instant();
        List<UUID> dangling = new ArrayList<>();
        for (Connection f : connections.findAttachedTo(microserviceId)) {
            // keep the names; clear the ids explicitly so the stored rows match the FK SET NULL
            f.detach(microserviceId, now);
            connections.save(f);
            dangling.add(f.getId());
        }
        connections.flush();
        return dangling;
    }

    @Override
    public void renamed(UUID microserviceId, String newName) {
        Instant now = clock.instant();
        for (Connection f : connections.findAttachedTo(microserviceId)) {
            f.endRenamed(microserviceId, newName, now);
            connections.save(f);
        }
    }

    @Override
    public List<UUID> reattach(UUID microserviceId, String name, String nameNormalized) {
        Instant now = clock.instant();
        List<UUID> ids = new ArrayList<>();
        for (Connection f : connections.findDanglingByName(nameNormalized)) {
            f.reattach(microserviceId, name, nameNormalized, now);
            connections.save(f);
            ids.add(f.getId());
        }
        return ids;
    }
}
