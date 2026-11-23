package com.refinements.visualizer.connection.adapter.in.microservice;

import com.refinements.visualizer.connection.application.port.in.ConnectionEnds;
import com.refinements.visualizer.microservice.spi.ConnectionEndsPort;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** The connection module's side of {@link ConnectionEndsPort} (research R4). Runs in the caller's transaction. */
@Component
class ConnectionEndsAdapter implements ConnectionEndsPort {

    private final ConnectionEnds ends;

    ConnectionEndsAdapter(ConnectionEnds ends) {
        this.ends = ends;
    }

    @Override
    public List<UUID> detach(UUID microserviceId) {
        return ends.detach(microserviceId);
    }

    @Override
    public void renamed(UUID microserviceId, String newName) {
        ends.renamed(microserviceId, newName);
    }

    @Override
    public List<UUID> reattach(UUID microserviceId, String name, String nameNormalized) {
        return ends.reattach(microserviceId, name, nameNormalized);
    }
}
