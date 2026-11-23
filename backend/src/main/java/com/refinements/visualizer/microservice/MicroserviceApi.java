package com.refinements.visualizer.microservice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask the microservice module (contracts/module-apis.md §microservice). The
 * {@code *WithoutRevision} operations are for the connections document apply, which bumps the workspace revision
 * once itself; they must run inside the caller's transaction.
 */
public interface MicroserviceApi {

    /** @throws com.refinements.visualizer.shared.errors.NotFoundException when there is no such microservice */
    MicroserviceView find(UUID id);

    List<MicroserviceView> findAll();

    /** All microservices ordered by normalized name. */
    List<MicroserviceView> findAllOrdered();

    Optional<MicroserviceView> findByNameNormalized(String nameNormalized);

    /** Creates a microservice (validated name and description) without bumping the revision. */
    MicroserviceView createWithoutRevision(String name, String description);

    /** Sets the description (validated) without bumping the revision. */
    void describeWithoutRevision(UUID id, String description);

    /** Sets ownership (validated) without bumping the revision. */
    void ownWithoutRevision(UUID id, String team, String repositoryUrl, String docsUrl);

    /** Re-attaches dangling connection ends whose kept name matches this microservice; returns their ids. */
    List<UUID> reattach(UUID id);
}
