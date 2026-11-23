package com.refinements.visualizer.card;

import com.refinements.visualizer.card.dto.ImportCardRequest;
import com.refinements.visualizer.card.dto.SpecCardSummary;
import com.refinements.visualizer.common.VisualizerProperties;
import com.refinements.visualizer.common.errors.BadRequestException;
import com.refinements.visualizer.common.errors.ConflictException;
import com.refinements.visualizer.common.errors.FetchFailedException;
import com.refinements.visualizer.common.errors.NotFoundException;
import com.refinements.visualizer.common.errors.ParseError;
import com.refinements.visualizer.common.errors.PayloadTooLargeException;
import com.refinements.visualizer.common.errors.SpecInvalidException;
import com.refinements.visualizer.ingestion.FieldFlattener;
import com.refinements.visualizer.ingestion.ParsedSpec;
import com.refinements.visualizer.ingestion.SpecFetcher;
import com.refinements.visualizer.ingestion.SpecKeys;
import com.refinements.visualizer.ingestion.SpecParser;
import com.refinements.visualizer.ingestion.model.ParsedEndpoint;
import com.refinements.visualizer.microservice.Microservice;
import com.refinements.visualizer.microservice.MicroserviceRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Import, re-sync and delete spec cards (FR-001 – FR-009). Cards never touch connections (FR-007): this class has
 * no dependency on the connection package. A failed re-sync persists its FAILED status while the request still
 * returns the error, so sync is not wrapped in one transaction.
 */
@Service
public class SpecCardService {

    private final SpecCardRepository cards;
    private final MicroserviceRepository microservices;
    private final SpecFetcher fetcher;
    private final SpecParser parser;
    private final FieldFlattener flattener;
    private final Clock clock;
    private final long maxBytes;

    public SpecCardService(
            SpecCardRepository cards,
            MicroserviceRepository microservices,
            SpecFetcher fetcher,
            SpecParser parser,
            FieldFlattener flattener,
            Clock clock,
            VisualizerProperties props) {
        this.cards = cards;
        this.microservices = microservices;
        this.fetcher = fetcher;
        this.parser = parser;
        this.flattener = flattener;
        this.clock = clock;
        this.maxBytes = props.spec().maxBytes();
    }

    public List<SpecCardSummary> list() {
        Map<UUID, UUID> links = new HashMap<>();
        for (Microservice m : microservices.findAll()) {
            if (m.getSpecCardId() != null) {
                links.put(m.getSpecCardId(), m.getId());
            }
        }
        return cards.findAllByOrderByCreatedAtDesc().stream()
                .map(c -> SpecCardSummary.from(c, false, links.get(c.getId())))
                .toList();
    }

    /** Imports a spec as a new, unlinked card. Duplicates by SpecCard.contentHash or SpecCard.sourceUrl → 409. */
    public SpecCardSummary importCard(ImportCardRequest request) {
        URI base = null;
        String content;
        if (request.sourceType() == SourceType.URL) {
            base = SpecFetcher.validateUrl(request.url());
            Optional<SpecCard> sameUrl = cards.findFirstBySourceUrl(base.toString());
            if (sameUrl.isPresent()) {
                throw duplicate(sameUrl.get(), "A card for this URL is already on the canvas. Re-sync it instead.");
            }
            content = fetcher.fetch(base);
        } else {
            content = request.content();
        }
        checkSize(content);
        ParsedSpec parsed = parser.parse(content, base);
        String hash = SpecKeys.contentHash(content);
        Optional<SpecCard> sameContent = cards.findFirstByContentHash(hash);
        if (sameContent.isPresent()) {
            throw duplicate(sameContent.get(), "This spec is already on the canvas.");
        }
        SpecCard card = new SpecCard(request.sourceType(), base == null ? null : base.toString(), clock.instant());
        apply(card, parsed, content, hash);
        return SpecCardSummary.from(card, false, null);
    }

    public SpecCardSummary sync(UUID cardId, String newContent) {
        SpecCard card = find(cardId);
        boolean hasContent = newContent != null && !newContent.isBlank();
        if (card.getSourceType() == SourceType.PASTE && !hasContent) {
            throw new BadRequestException("A pasted card is re-synced by sending its new content.");
        }
        URI base = card.getSourceUrl() == null ? null : URI.create(card.getSourceUrl());
        String content;
        ParsedSpec parsed;
        try {
            content = hasContent ? newContent : fetcher.fetch(base);
            checkSize(content);
            parsed = parser.parse(content, base);
        } catch (SpecInvalidException e) {
            fail(card, e.getErrors());
            throw e;
        } catch (FetchFailedException | PayloadTooLargeException e) {
            fail(card, List.of(new ParseError(null, e.getMessage())));
            throw e;
        }
        String hash = SpecKeys.contentHash(content);
        boolean unchanged = hash.equals(card.getContentHash()) && card.getLastSyncStatus() == SyncStatus.OK;
        apply(card, parsed, content, hash);
        return SpecCardSummary.from(card, unchanged, linkedMicroservice(cardId));
    }

    /** Deletes the card; its microservice (if any) becomes spec-less through the FK. Connections are untouched. */
    @Transactional
    public void delete(UUID cardId) {
        SpecCard card = find(cardId);
        microservices.findBySpecCardId(cardId).ifPresent(m -> m.link(null, clock.instant()));
        microservices.flush();
        cards.delete(card);
    }

    private SpecCard find(UUID cardId) {
        return cards.findById(cardId).orElseThrow(() -> new NotFoundException("No spec card " + cardId + "."));
    }

    private UUID linkedMicroservice(UUID cardId) {
        return microservices.findBySpecCardId(cardId).map(Microservice::getId).orElse(null);
    }

    private void apply(SpecCard card, ParsedSpec parsed, String content, String hash) {
        if (hash.equals(card.getContentHash())) {
            card.markSynced(clock.instant());
        } else {
            List<ParsedEndpoint> endpoints =
                    flattener.flatten(parsed.openApi(), card.getId().toString());
            card.applyParsed(
                    parsed.title(), parsed.version(), parsed.format(), content, hash, endpoints, clock.instant());
        }
        cards.save(card);
    }

    private void fail(SpecCard card, List<ParseError> errors) {
        card.markFailed(errors, clock.instant());
        cards.save(card);
    }

    private static ConflictException duplicate(SpecCard existing, String message) {
        return new ConflictException(
                message, ConflictException.DUPLICATE_CARD, Map.of("existingCardId", existing.getId()));
    }

    private void checkSize(String content) {
        if (content != null && content.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new PayloadTooLargeException("The spec exceeds the " + (maxBytes / (1024 * 1024)) + " MB limit.");
        }
    }
}
