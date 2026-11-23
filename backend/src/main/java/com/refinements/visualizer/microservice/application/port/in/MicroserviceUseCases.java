package com.refinements.visualizer.microservice.application.port.in;

import com.refinements.visualizer.microservice.MicroserviceView;
import java.util.List;
import java.util.UUID;

/** What the microservice module offers its inbound adapters (data-model.md §2, FR-012 – FR-016, FR-027, FR-028). */
public interface MicroserviceUseCases {

    record NewMicroservice(String name, String description, String team, String repositoryUrl, String docsUrl) {}

    /** A partial change; the {@code *Present} flags tell an absent property (unchanged) from an explicit null. */
    record MicroserviceChanges(
            String name,
            String description,
            boolean descriptionPresent,
            String team,
            boolean teamPresent,
            String repositoryUrl,
            boolean repositoryUrlPresent,
            String docsUrl,
            boolean docsUrlPresent) {}

    /** The microservice after a write, the connections that re-attached to it, and the new workspace revision. */
    record Mutation(MicroserviceView microservice, List<UUID> reattachedConnectionIds, long revision) {}

    record Deletion(List<UUID> danglingConnectionIds, long revision) {}

    Mutation create(NewMicroservice command);

    Mutation update(UUID id, MicroserviceChanges changes);

    /** Deletes; its connections keep their end names and become dangling. */
    Deletion delete(UUID id);

    /** Links a card; the microservice's current card is replaced only when {@code replace} is true. */
    void link(UUID id, UUID cardId, boolean replace);

    void unlink(UUID id);
}
