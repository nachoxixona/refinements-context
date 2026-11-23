package com.refinements.visualizer.microservice;

import com.refinements.visualizer.card.SpecCardRepository;
import com.refinements.visualizer.common.errors.BadRequestException;
import com.refinements.visualizer.common.errors.ConflictException;
import com.refinements.visualizer.common.errors.NotFoundException;
import com.refinements.visualizer.connection.Connection;
import com.refinements.visualizer.connection.ConnectionRepository;
import com.refinements.visualizer.connection.Names;
import com.refinements.visualizer.microservice.dto.MicroserviceDeleted;
import com.refinements.visualizer.microservice.dto.MicroserviceDto;
import com.refinements.visualizer.microservice.dto.MicroserviceMutation;
import com.refinements.visualizer.microservice.dto.MicroservicePatch;
import com.refinements.visualizer.workspace.WorkspaceRevision;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Microservices and their 1:1 spec link (data-model.md §2). Renames cascade to connection names (FR-028); deleting
 * leaves connections dangling (FR-016, FR-027); creating re-attaches dangling connections with the same name (research R2).
 */
@Service
public class MicroserviceService {

    private final MicroserviceRepository microservices;
    private final SpecCardRepository cards;
    private final ConnectionRepository connections;
    private final WorkspaceRevision revision;
    private final Clock clock;

    public MicroserviceService(
            MicroserviceRepository microservices,
            SpecCardRepository cards,
            ConnectionRepository connections,
            WorkspaceRevision revision,
            Clock clock) {
        this.microservices = microservices;
        this.cards = cards;
        this.connections = connections;
        this.revision = revision;
        this.clock = clock;
    }

    @Transactional
    public MicroserviceMutation create(String name, String description) {
        return create(name, description, null, null, null);
    }

    /** Creates with ownership (spec 004 FR-013). */
    @Transactional
    public MicroserviceMutation create(
            String name, String description, String team, String repositoryUrl, String docsUrl) {
        Microservice m = createInternal(name, description);
        ownInternal(m, team, repositoryUrl, docsUrl);
        List<UUID> reattached = reattach(m);
        return new MicroserviceMutation(MicroserviceDto.from(m), reattached, revision.bump());
    }

    /**
     * Creates a microservice without bumping the revision (callers do), for the connections document as well.
     * Validates Microservice.name and Microservice.description (data-model.md §2).
     */
    public Microservice createInternal(String name, String description) {
        String clean = checkName(name);
        checkDescription(description);
        if (microservices.findByNameNormalized(Names.norm(clean)).isPresent()) {
            throw duplicateName(clean);
        }
        Microservice m = new Microservice(clean, description, clock.instant());
        return microservices.saveAndFlush(m);
    }

    /** Re-attaches dangling connection ends whose kept name equals this microservice's normalized name. */
    public List<UUID> reattach(Microservice m) {
        Instant now = clock.instant();
        List<UUID> ids = new ArrayList<>();
        for (Connection f : connections.findDanglingByName(m.getNameNormalized())) {
            if (f.getSourceId() == null && f.getSourceNameNormalized().equals(m.getNameNormalized())) {
                f.setSource(m.getId(), m.getName(), now);
            }
            if (f.getTargetId() == null && f.getTargetNameNormalized().equals(m.getNameNormalized())) {
                f.setTarget(m.getId(), m.getName(), now);
            }
            ids.add(f.getId());
        }
        return ids;
    }

    @Transactional
    public MicroserviceMutation update(UUID id, MicroservicePatch patch) {
        Microservice m = find(id);
        Instant now = clock.instant();
        if (patch.name() != null) {
            String clean = checkName(patch.name());
            Optional<Microservice> clash = microservices.findByNameNormalized(Names.norm(clean));
            if (clash.isPresent() && !clash.get().getId().equals(id)) {
                throw duplicateName(clean);
            }
            m.rename(clean, now);
            for (Connection f : connections.findBySourceIdOrTargetId(id, id)) {
                if (id.equals(f.getSourceId())) {
                    f.setSource(id, m.getName(), now);
                }
                if (id.equals(f.getTargetId())) {
                    f.setTarget(id, m.getName(), now);
                }
            }
        }
        if (patch.descriptionPresent()) {
            checkDescription(patch.description());
            m.describe(patch.description(), now);
        }
        if (patch.teamPresent() || patch.repositoryUrlPresent() || patch.docsUrlPresent()) {
            m.own(
                    patch.teamPresent() ? Ownership.team(patch.team()) : m.getTeam(),
                    patch.repositoryUrlPresent()
                            ? Ownership.url(patch.repositoryUrl(), "repositoryUrl")
                            : m.getRepositoryUrl(),
                    patch.docsUrlPresent() ? Ownership.url(patch.docsUrl(), "docsUrl") : m.getDocsUrl(),
                    now);
        }
        microservices.saveAndFlush(m);
        List<UUID> reattached = patch.name() == null ? List.of() : reattach(m);
        return new MicroserviceMutation(MicroserviceDto.from(m), reattached, revision.bump());
    }

    /** Sets ownership (validated) without bumping the revision. */
    public void ownInternal(Microservice m, String team, String repositoryUrl, String docsUrl) {
        m.own(
                Ownership.team(team),
                Ownership.url(repositoryUrl, "repositoryUrl"),
                Ownership.url(docsUrl, "docsUrl"),
                clock.instant());
        microservices.saveAndFlush(m);
    }

    /** Describes an existing microservice without bumping the revision (connections document apply). */
    public void describeInternal(Microservice m, String description) {
        checkDescription(description);
        m.describe(description, clock.instant());
    }

    @Transactional
    public MicroserviceDeleted delete(UUID id) {
        Microservice m = find(id);
        List<UUID> dangling = new ArrayList<>();
        Instant now = clock.instant();
        for (Connection f : connections.findBySourceIdOrTargetId(id, id)) {
            // keep the names; null the ids explicitly so the in-memory entities match the FK SET NULL
            if (id.equals(f.getSourceId())) {
                f.setSource(null, f.getSourceName(), now);
            }
            if (id.equals(f.getTargetId())) {
                f.setTarget(null, f.getTargetName(), now);
            }
            dangling.add(f.getId());
        }
        connections.flush();
        microservices.delete(m);
        return new MicroserviceDeleted(dangling, revision.bump());
    }

    /** Links a card (FR-012, FR-013). The card must be unlinked; the microservice's card is replaced only on request. */
    @Transactional
    public void link(UUID id, UUID cardId, boolean replace) {
        Microservice m = find(id);
        if (!cards.existsById(cardId)) {
            throw new NotFoundException("No spec card " + cardId + ".");
        }
        if (cardId.equals(m.getSpecCardId())) {
            return;
        }
        Optional<Microservice> owner = microservices.findBySpecCardId(cardId);
        if (owner.isPresent()) {
            throw new ConflictException(
                    "This spec is already linked to '" + owner.get().getName() + "'. Unlink it there first.",
                    ConflictException.CARD_ALREADY_LINKED);
        }
        if (m.getSpecCardId() != null && !replace) {
            throw new ConflictException(
                    "'" + m.getName() + "' already has a spec. Replace it?", ConflictException.MICROSERVICE_HAS_CARD);
        }
        m.link(cardId, clock.instant());
        microservices.saveAndFlush(m);
    }

    @Transactional
    public void unlink(UUID id) {
        Microservice m = find(id);
        m.link(null, clock.instant());
        microservices.saveAndFlush(m);
    }

    public Microservice find(UUID id) {
        return microservices.findById(id).orElseThrow(() -> new NotFoundException("No microservice " + id + "."));
    }

    private static String checkName(String name) {
        String clean = Names.clean(name);
        if (clean.isEmpty()) {
            throw new BadRequestException("A microservice needs a name.");
        }
        if (clean.length() > Names.MAX_NAME) {
            throw new BadRequestException("Microservice names are at most " + Names.MAX_NAME + " characters.");
        }
        return clean;
    }

    private static void checkDescription(String description) {
        if (description != null && description.length() > Names.MAX_DESCRIPTION) {
            throw new BadRequestException("Descriptions are at most " + Names.MAX_DESCRIPTION + " characters.");
        }
    }

    private static ConflictException duplicateName(String name) {
        return new ConflictException(
                "A microservice named '" + name + "' already exists.", ConflictException.DUPLICATE_NAME);
    }
}
