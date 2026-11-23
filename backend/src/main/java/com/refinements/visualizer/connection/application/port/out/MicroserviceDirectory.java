package com.refinements.visualizer.connection.application.port.out;

import com.refinements.visualizer.microservice.MicroserviceView;
import java.util.List;
import java.util.UUID;

/**
 * The microservices connections run between (the microservice module behind it). The {@code *WithoutRevision}
 * operations are for the connections document, which bumps the revision once itself.
 */
public interface MicroserviceDirectory {

    /** @throws com.refinements.visualizer.shared.errors.NotFoundException when there is no such microservice */
    MicroserviceView find(UUID id);

    List<MicroserviceView> findAll();

    MicroserviceView createWithoutRevision(String name, String description);

    void describeWithoutRevision(UUID id, String description);

    void ownWithoutRevision(UUID id, String team, String repository, String docs);

    /** Points dangling connection ends named like the microservice at it again. */
    void reattach(UUID id);
}
