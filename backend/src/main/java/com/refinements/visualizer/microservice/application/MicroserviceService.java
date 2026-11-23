package com.refinements.visualizer.microservice.application;

import com.refinements.visualizer.microservice.MicroserviceApi;
import com.refinements.visualizer.microservice.MicroserviceView;
import com.refinements.visualizer.microservice.application.port.in.MicroserviceUseCases;
import com.refinements.visualizer.microservice.application.port.out.CardCatalog;
import com.refinements.visualizer.microservice.application.port.out.MicroserviceStore;
import com.refinements.visualizer.microservice.application.port.out.Revision;
import com.refinements.visualizer.microservice.domain.Microservice;
import com.refinements.visualizer.microservice.domain.Ownership;
import com.refinements.visualizer.microservice.spi.ConnectionEndsPort;
import com.refinements.visualizer.shared.Names;
import com.refinements.visualizer.shared.errors.ConflictException;
import com.refinements.visualizer.shared.errors.NotFoundException;
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
 * leaves connections dangling (FR-016, FR-027); creating re-attaches dangling connections with the same name
 * (research R2). The order of connection-end changes, flushes, deletes and revision bumps follows specs/005
 * data-model.md "Behaviours whose ordering must be preserved".
 */
@Service
class MicroserviceService implements MicroserviceApi, MicroserviceUseCases {

    private final MicroserviceStore microservices;
    private final CardCatalog cards;
    private final ConnectionEndsPort connectionEnds;
    private final Revision revision;
    private final Clock clock;

    MicroserviceService(
            MicroserviceStore microservices,
            CardCatalog cards,
            ConnectionEndsPort connectionEnds,
            Revision revision,
            Clock clock) {
        this.microservices = microservices;
        this.cards = cards;
        this.connectionEnds = connectionEnds;
        this.revision = revision;
        this.clock = clock;
    }

    // --- MicroserviceUseCases --------------------------------------------------------------------------------

    @Override
    @Transactional
    public Mutation create(NewMicroservice command) {
        Microservice m = createInternal(command.name(), command.description());
        ownInternal(m, command.team(), command.repositoryUrl(), command.docsUrl());
        List<UUID> reattached = reattach(m);
        return new Mutation(view(m), reattached, revision.bump());
    }

    @Override
    @Transactional
    public Mutation update(UUID id, MicroserviceChanges changes) {
        Microservice m = load(id);
        Instant now = clock.instant();
        if (changes.name() != null) {
            String clean = Microservice.checkName(changes.name());
            Optional<Microservice> clash = microservices.findByNameNormalized(Names.norm(clean));
            if (clash.isPresent() && !clash.get().getId().equals(id)) {
                throw duplicateName(clean);
            }
            m.rename(clean, now);
            connectionEnds.renamed(id, m.getName());
        }
        if (changes.descriptionPresent()) {
            Microservice.checkDescription(changes.description());
            m.describe(changes.description(), now);
        }
        if (changes.teamPresent() || changes.repositoryUrlPresent() || changes.docsUrlPresent()) {
            m.own(
                    changes.teamPresent() ? Ownership.team(changes.team()) : m.getTeam(),
                    changes.repositoryUrlPresent()
                            ? Ownership.url(changes.repositoryUrl(), "repositoryUrl")
                            : m.getRepositoryUrl(),
                    changes.docsUrlPresent() ? Ownership.url(changes.docsUrl(), "docsUrl") : m.getDocsUrl(),
                    now);
        }
        microservices.save(m);
        List<UUID> reattached = changes.name() == null ? List.of() : reattach(m);
        return new Mutation(view(m), reattached, revision.bump());
    }

    @Override
    @Transactional
    public Deletion delete(UUID id) {
        load(id);
        // keeps the names and nulls the ids (matching the FK SET NULL), flushed before the row goes
        List<UUID> dangling = connectionEnds.detach(id);
        microservices.delete(id);
        return new Deletion(dangling, revision.bump());
    }

    @Override
    @Transactional
    public void link(UUID id, UUID cardId, boolean replace) {
        Microservice m = load(id);
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
        microservices.save(m);
    }

    @Override
    @Transactional
    public void unlink(UUID id) {
        Microservice m = load(id);
        m.link(null, clock.instant());
        microservices.save(m);
    }

    // --- MicroserviceApi -------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public MicroserviceView find(UUID id) {
        return view(load(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MicroserviceView> findAll() {
        return microservices.findAll().stream().map(MicroserviceService::view).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MicroserviceView> findAllOrdered() {
        return microservices.findAllOrdered().stream()
                .map(MicroserviceService::view)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MicroserviceView> findByNameNormalized(String nameNormalized) {
        return microservices.findByNameNormalized(nameNormalized).map(MicroserviceService::view);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public MicroserviceView createWithoutRevision(String name, String description) {
        return view(createInternal(name, description));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void describeWithoutRevision(UUID id, String description) {
        Microservice m = load(id);
        Microservice.checkDescription(description);
        m.describe(description, clock.instant());
        microservices.save(m);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void ownWithoutRevision(UUID id, String team, String repositoryUrl, String docsUrl) {
        ownInternal(load(id), team, repositoryUrl, docsUrl);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<UUID> reattach(UUID id) {
        return reattach(load(id));
    }

    // --- helpers ---------------------------------------------------------------------------------------------

    /** Creates without bumping the revision; validates Microservice.name and .description (data-model.md §2). */
    private Microservice createInternal(String name, String description) {
        String clean = Microservice.checkName(name);
        Microservice.checkDescription(description);
        if (microservices.findByNameNormalized(Names.norm(clean)).isPresent()) {
            throw duplicateName(clean);
        }
        Microservice m = Microservice.create(clean, description, clock.instant());
        microservices.save(m);
        return m;
    }

    /** Sets ownership (validated) without bumping the revision. */
    private void ownInternal(Microservice m, String team, String repositoryUrl, String docsUrl) {
        m.own(
                Ownership.team(team),
                Ownership.url(repositoryUrl, "repositoryUrl"),
                Ownership.url(docsUrl, "docsUrl"),
                clock.instant());
        microservices.save(m);
    }

    /** Re-attaches dangling connection ends whose kept name equals this microservice's normalized name. */
    private List<UUID> reattach(Microservice m) {
        return connectionEnds.reattach(m.getId(), m.getName(), m.getNameNormalized());
    }

    private Microservice load(UUID id) {
        return microservices.findById(id).orElseThrow(() -> new NotFoundException("No microservice " + id + "."));
    }

    private static ConflictException duplicateName(String name) {
        return new ConflictException(
                "A microservice named '" + name + "' already exists.", ConflictException.DUPLICATE_NAME);
    }

    private static MicroserviceView view(Microservice m) {
        return new MicroserviceView(
                m.getId(),
                m.getName(),
                m.getNameNormalized(),
                m.getDescription(),
                m.getSpecCardId(),
                m.getTeam(),
                m.getRepositoryUrl(),
                m.getDocsUrl());
    }
}
