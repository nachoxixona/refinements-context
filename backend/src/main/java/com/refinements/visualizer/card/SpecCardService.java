package com.refinements.visualizer.card;

import com.refinements.visualizer.card.adapter.out.fetch.SpecFetcher;
import com.refinements.visualizer.card.adapter.out.parser.FieldFlattener;
import com.refinements.visualizer.card.adapter.out.parser.ParsedSpec;
import com.refinements.visualizer.card.adapter.out.parser.SpecParser;
import com.refinements.visualizer.card.dto.ImportCardRequest;
import com.refinements.visualizer.card.dto.SpecCardSummary;
import com.refinements.visualizer.card.model.ParsedEndpoint;
import com.refinements.visualizer.card.model.SpecKeys;
import com.refinements.visualizer.card.spi.CardLinkPort;
import com.refinements.visualizer.shared.VisualizerProperties;
import com.refinements.visualizer.shared.errors.BadRequestException;
import com.refinements.visualizer.shared.errors.ConflictException;
import com.refinements.visualizer.shared.errors.FetchFailedException;
import com.refinements.visualizer.shared.errors.NotFoundException;
import com.refinements.visualizer.shared.errors.ParseError;
import com.refinements.visualizer.shared.errors.PayloadTooLargeException;
import com.refinements.visualizer.shared.errors.SpecInvalidException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Import, re-sync and delete spec cards (FR-001 – FR-009). Cards never touch connections (FR-007): this class has
 * no dependency on the connection package. A failed re-sync persists its FAILED status while the request still
 * returns the error, so sync is not wrapped in one transaction.
 */
@Service
public class SpecCardService implements SpecCardApi {

    private final SpecCardRepository cards;
    private final CardLinkPort links;
    private final SpecFetcher fetcher;
    private final SpecParser parser;
    private final FieldFlattener flattener;
    private final Clock clock;
    private final long maxBytes;

    public SpecCardService(
            SpecCardRepository cards,
            CardLinkPort links,
            SpecFetcher fetcher,
            SpecParser parser,
            FieldFlattener flattener,
            Clock clock,
            VisualizerProperties props) {
        this.cards = cards;
        this.links = links;
        this.fetcher = fetcher;
        this.parser = parser;
        this.flattener = flattener;
        this.clock = clock;
        this.maxBytes = props.spec().maxBytes();
    }

    public List<SpecCardSummary> list() {
        Map<UUID, UUID> linked = links.linksByCard();
        return cards.findAllByOrderByCreatedAtDesc().stream()
                .map(c -> SpecCardSummary.from(c, false, linked.get(c.getId())))
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
        links.unlinkCard(cardId);
        cards.delete(card);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID cardId) {
        return cards.existsById(cardId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpecCardView> findAll() {
        return cards.findAll().stream()
                .sorted(Comparator.comparing(SpecCard::getCreatedAt).thenComparing(SpecCard::getId))
                .map(SpecCardView::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> allIds() {
        return Set.copyOf(cards.findAllIds());
    }

    private SpecCard find(UUID cardId) {
        return cards.findById(cardId).orElseThrow(() -> new NotFoundException("No spec card " + cardId + "."));
    }

    private UUID linkedMicroservice(UUID cardId) {
        return links.linkedMicroservice(cardId).orElse(null);
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
