package com.refinements.visualizer.card.application;

import com.refinements.visualizer.card.SourceType;
import com.refinements.visualizer.card.SpecCardApi;
import com.refinements.visualizer.card.SpecCardView;
import com.refinements.visualizer.card.application.port.in.SpecCardUseCases;
import com.refinements.visualizer.card.application.port.out.SpecCardStore;
import com.refinements.visualizer.card.application.port.out.SpecParserPort;
import com.refinements.visualizer.card.application.port.out.SpecParserPort.ParsedSpecification;
import com.refinements.visualizer.card.application.port.out.SpecSource;
import com.refinements.visualizer.card.domain.SpecCard;
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
 * Import, re-sync and delete spec cards (FR-001 – FR-009). Cards never touch connections (FR-007).
 *
 * <p>{@link #importCard} and {@link #sync} deliberately run without a surrounding transaction (as before 005): a
 * failed re-sync saves the FAILED status and then rethrows, and that save must not be rolled back.
 */
@Service
class SpecCardService implements SpecCardApi, SpecCardUseCases {

    private final SpecCardStore cards;
    private final CardLinkPort links;
    private final SpecSource source;
    private final SpecParserPort parser;
    private final Clock clock;
    private final long maxBytes;

    SpecCardService(
            SpecCardStore cards,
            CardLinkPort links,
            SpecSource source,
            SpecParserPort parser,
            Clock clock,
            VisualizerProperties props) {
        this.cards = cards;
        this.links = links;
        this.source = source;
        this.parser = parser;
        this.clock = clock;
        this.maxBytes = props.spec().maxBytes();
    }

    // --- SpecCardUseCases ----------------------------------------------------------------------------------

    @Override
    public List<CardResult> list() {
        Map<UUID, UUID> linked = links.linksByCard();
        return cards.findAllNewestFirst().stream()
                .map(c -> result(c, false, linked.get(c.getId())))
                .toList();
    }

    @Override
    public CardResult importCard(ImportCard command) {
        URI base = null;
        String content;
        if (command.sourceType() == SourceType.URL) {
            base = source.validate(command.url());
            Optional<SpecCard> sameUrl = cards.findFirstBySourceUrl(base.toString());
            if (sameUrl.isPresent()) {
                throw duplicate(sameUrl.get(), "A card for this URL is already on the canvas. Re-sync it instead.");
            }
            content = source.fetch(base);
        } else {
            content = command.content();
        }
        checkSize(content);
        UUID id = UUID.randomUUID();
        ParsedSpecification parsed = parser.parse(content, base, id.toString());
        String hash = SpecKeys.contentHash(content);
        Optional<SpecCard> sameContent = cards.findFirstByContentHash(hash);
        if (sameContent.isPresent()) {
            throw duplicate(sameContent.get(), "This spec is already on the canvas.");
        }
        SpecCard card =
                SpecCard.create(id, command.sourceType(), base == null ? null : base.toString(), clock.instant());
        apply(card, parsed, content, hash);
        return result(card, false, null);
    }

    @Override
    public CardResult sync(UUID cardId, String newContent) {
        SpecCard card = find(cardId);
        boolean hasContent = newContent != null && !newContent.isBlank();
        if (card.getSourceType() == SourceType.PASTE && !hasContent) {
            throw new BadRequestException("A pasted card is re-synced by sending its new content.");
        }
        URI base = card.getSourceUrl() == null ? null : URI.create(card.getSourceUrl());
        String content;
        ParsedSpecification parsed;
        try {
            content = hasContent ? newContent : source.fetch(base);
            checkSize(content);
            parsed = parser.parse(content, base, card.getId().toString());
        } catch (SpecInvalidException e) {
            fail(card, e.getErrors());
            throw e;
        } catch (FetchFailedException | PayloadTooLargeException e) {
            fail(card, List.of(new ParseError(null, e.getMessage())));
            throw e;
        }
        String hash = SpecKeys.contentHash(content);
        boolean unchanged = card.isUnchangedBy(hash);
        apply(card, parsed, content, hash);
        return result(card, unchanged, links.linkedMicroservice(cardId).orElse(null));
    }

    @Override
    @Transactional
    public void delete(UUID cardId) {
        find(cardId);
        links.unlinkCard(cardId); // flushes the unlink before the card row goes (data-model.md ordering table)
        cards.delete(cardId);
    }

    // --- SpecCardApi ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public boolean exists(UUID cardId) {
        return cards.exists(cardId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpecCardView> findAll() {
        return cards.findAll().stream()
                .sorted(Comparator.comparing(SpecCard::getCreatedAt).thenComparing(SpecCard::getId))
                .map(SpecCardService::view)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> allIds() {
        return cards.allIds();
    }

    // --- helpers -------------------------------------------------------------------------------------------

    private SpecCard find(UUID cardId) {
        return cards.findById(cardId).orElseThrow(() -> new NotFoundException("No spec card " + cardId + "."));
    }

    private void apply(SpecCard card, ParsedSpecification parsed, String content, String hash) {
        if (hash.equals(card.getContentHash())) {
            card.markSynced(clock.instant());
        } else {
            card.applyParsed(
                    parsed.title(),
                    parsed.version(),
                    parsed.format(),
                    content,
                    hash,
                    parsed.endpoints(),
                    clock.instant());
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

    private static CardResult result(SpecCard c, boolean unchanged, UUID linkedMicroserviceId) {
        return new CardResult(view(c), List.copyOf(c.getLastSyncErrors()), unchanged, linkedMicroserviceId);
    }

    static SpecCardView view(SpecCard c) {
        return new SpecCardView(
                c.getId(),
                c.getTitle(),
                c.getApiVersion(),
                c.getSpecFormat(),
                c.getSourceType(),
                c.getSourceUrl(),
                c.getContentHash(),
                c.getLastSyncAt(),
                c.getLastSyncStatus(),
                List.copyOf(c.getEndpoints()),
                c.getCreatedAt());
    }
}
