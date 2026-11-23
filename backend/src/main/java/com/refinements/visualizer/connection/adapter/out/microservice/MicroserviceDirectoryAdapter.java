package com.refinements.visualizer.connection.adapter.out.microservice;

import com.refinements.visualizer.connection.application.port.out.MicroserviceDirectory;
import com.refinements.visualizer.microservice.MicroserviceApi;
import com.refinements.visualizer.microservice.MicroserviceView;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** {@link MicroserviceDirectory} through the microservice module's API. */
@Component
class MicroserviceDirectoryAdapter implements MicroserviceDirectory {

    private final MicroserviceApi microservices;

    MicroserviceDirectoryAdapter(MicroserviceApi microservices) {
        this.microservices = microservices;
    }

    @Override
    public MicroserviceView find(UUID id) {
        return microservices.find(id);
    }

    @Override
    public List<MicroserviceView> findAll() {
        return microservices.findAll();
    }

    @Override
    public MicroserviceView createWithoutRevision(String name, String description) {
        return microservices.createWithoutRevision(name, description);
    }

    @Override
    public void describeWithoutRevision(UUID id, String description) {
        microservices.describeWithoutRevision(id, description);
    }

    @Override
    public void ownWithoutRevision(UUID id, String team, String repository, String docs) {
        microservices.ownWithoutRevision(id, team, repository, docs);
    }

    @Override
    public void reattach(UUID id) {
        microservices.reattach(id);
    }
}
