package com.refinements.visualizer.microservice;

import com.refinements.visualizer.card.SpecCardApi;
import com.refinements.visualizer.microservice.dto.MicroserviceDeleted;
import com.refinements.visualizer.microservice.dto.MicroserviceDto;
import com.refinements.visualizer.microservice.dto.MicroserviceMutation;
import com.refinements.visualizer.microservice.dto.MicroservicePatch;
import com.refinements.visualizer.microservice.spi.ConnectionEndsPort;
import com.refinements.visualizer.shared.Names;
import com.refinements.visualizer.shared.errors.BadRequestException;
import com.refinements.visualizer.shared.errors.ConflictException;
import com.refinements.visualizer.shared.errors.NotFoundException;
import com.refinements.visualizer.workspace.WorkspaceRevision;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Microservices and their 1:1 spec link (data-model.md §2). Renames cascade to connection names (FR-028); deleting
 * leaves connections dangling (FR-016, FR-027); creating re-attaches dangling connections with the same name (research R2).
 */
@Service
public class MicroserviceService implements MicroserviceApi {

    private final MicroserviceRepository microservices;
    private final SpecCardApi cards;
    private final ConnectionEndsPort connectionEnds;
    private final WorkspaceRevision revision;
    private final Clock clock;

    public MicroserviceService(
            MicroserviceRepository microservices,
            SpecCardApi cards,
            ConnectionEndsPort connectionEnds,
            WorkspaceRevision revision,
            Clock clock) {
        this.microservices = microservices;
        this.cards = cards;
        this.connectionEnds = connectionEnds;
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
    Microservice createInternal(String name, String description) {
        String clean = checkName(name);
        checkDescription(description);
        if (microservices.findByNameNormalized(Names.norm(clean)).isPresent()) {
            throw duplicateName(clean);
        }
        Microservice m = new Microservice(clean, description, clock.instant());
        return microservices.saveAndFlush(m);
    }

    /** Re-attaches dangling connection ends whose kept name equals this microservice's normalized name. */
    List<UUID> reattach(Microservice m) {
        return connectionEnds.reattach(m.getId(), m.getName(), m.getNameNormalized());
    }

    @Transactional
    public MicroserviceMutation update(UUID id, MicroservicePatch patch) {
        Microservice m = entity(id);
        Instant now = clock.instant();
        if (patch.name() != null) {
            String clean = checkName(patch.name());
            Optional<Microservice> clash = microservices.findByNameNormalized(Names.norm(clean));
            if (clash.isPresent() && !clash.get().getId().equals(id)) {
                throw duplicateName(clean);
            }
            m.rename(clean, now);
            connectionEnds.renamed(id, m.getName());
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
    void ownInternal(Microservice m, String team, String repositoryUrl, String docsUrl) {
        m.own(
                Ownership.team(team),
                Ownership.url(repositoryUrl, "repositoryUrl"),
                Ownership.url(docsUrl, "docsUrl"),
                clock.instant());
        microservices.saveAndFlush(m);
    }

    /** Describes an existing microservice without bumping the revision (connections document apply). */
    void describeInternal(Microservice m, String description) {
        checkDescription(description);
        m.describe(description, clock.instant());
    }

    @Transactional
    public MicroserviceDeleted delete(UUID id) {
        Microservice m = entity(id);
        // keeps the names and nulls the ids (matching the FK SET NULL), flushed before the row goes
        List<UUID> dangling = connectionEnds.detach(id);
        microservices.delete(m);
        return new MicroserviceDeleted(dangling, revision.bump());
    }

    /** Links a card (FR-012, FR-013). The card must be unlinked; the microservice's card is replaced only on request. */
    @Transactional
    public void link(UUID id, UUID cardId, boolean replace) {
        Microservice m = entity(id);
        if (!cards.exists(cardId)) {
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
        Microservice m = entity(id);
        m.link(null, clock.instant());
        microservices.saveAndFlush(m);
    }

    Microservice entity(UUID id) {
        return microservices.findById(id).orElseThrow(() -> new NotFoundException("No microservice " + id + "."));
    }

    // --- MicroserviceApi -------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public MicroserviceView find(UUID id) {
        return MicroserviceView.from(entity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MicroserviceView> findAll() {
        return microservices.findAll().stream().map(MicroserviceView::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MicroserviceView> findAllOrdered() {
        return microservices.findAllByOrderByNameNormalizedAsc().stream()
                .map(MicroserviceView::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MicroserviceView> findByNameNormalized(String nameNormalized) {
        return microservices.findByNameNormalized(nameNormalized).map(MicroserviceView::from);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public MicroserviceView createWithoutRevision(String name, String description) {
        return MicroserviceView.from(createInternal(name, description));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void describeWithoutRevision(UUID id, String description) {
        describeInternal(entity(id), description);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void ownWithoutRevision(UUID id, String team, String repositoryUrl, String docsUrl) {
        ownInternal(entity(id), team, repositoryUrl, docsUrl);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> reattach(UUID id) {
        return reattach(entity(id));
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
