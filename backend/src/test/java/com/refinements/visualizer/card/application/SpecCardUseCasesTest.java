package com.refinements.visualizer.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.refinements.visualizer.card.SourceType;
import com.refinements.visualizer.card.SyncStatus;
import com.refinements.visualizer.card.application.port.in.SpecCardUseCases.CardResult;
import com.refinements.visualizer.card.application.port.in.SpecCardUseCases.ImportCard;
import com.refinements.visualizer.card.application.port.out.SpecCardStore;
import com.refinements.visualizer.card.application.port.out.SpecParserPort;
import com.refinements.visualizer.card.application.port.out.SpecSource;
import com.refinements.visualizer.card.domain.SpecCard;
import com.refinements.visualizer.card.model.SpecFormat;
import com.refinements.visualizer.card.spi.CardLinkPort;
import com.refinements.visualizer.shared.errors.BadRequestException;
import com.refinements.visualizer.shared.errors.ConflictException;
import com.refinements.visualizer.shared.errors.SpecInvalidException;
import com.refinements.visualizer.support.TestSpecs;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The card use cases with in-memory ports: no Spring, no database, no HTTP (specs/005 SC-005). */
class SpecCardUseCasesTest {

    private final InMemoryCards store = new InMemoryCards();
    private final FakeLinks links = new FakeLinks();
    private final SpecCardService service = new SpecCardService(
            store,
            links,
            new NoSource(),
            new FakeParser(),
            Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC),
            TestSpecs.props());

    @Test
    void importingTheSameContentTwiceIsAConflict() {
        CardResult first = service.importCard(new ImportCard(SourceType.PASTE, "spec: one", null));
        assertThat(first.card().title()).isEqualTo("Fake API");
        assertThat(first.card().endpoints()).isEmpty();

        assertThatThrownBy(() -> service.importCard(new ImportCard(SourceType.PASTE, "spec: one", null)))
                .isInstanceOf(ConflictException.class);
        assertThat(store.cards).hasSize(1);
    }

    @Test
    void aFailedResyncIsSavedAsFailedAndRethrown() {
        UUID id = service.importCard(new ImportCard(SourceType.PASTE, "spec: one", null))
                .card()
                .id();
        assertThatThrownBy(() -> service.sync(id, "broken")).isInstanceOf(SpecInvalidException.class);
        assertThat(store.cards.get(id).getLastSyncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(store.cards.get(id).getContentHash()).isNotNull();
    }

    @Test
    void pastedCardsNeedNewContentToResync() {
        UUID id = service.importCard(new ImportCard(SourceType.PASTE, "spec: one", null))
                .card()
                .id();
        assertThatThrownBy(() -> service.sync(id, null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void deletingUnlinksTheMicroserviceFirst() {
        UUID id = service.importCard(new ImportCard(SourceType.PASTE, "spec: one", null))
                .card()
                .id();
        service.delete(id);
        assertThat(links.unlinked).containsExactly(id);
        assertThat(store.cards).isEmpty();
    }

    // --- in-memory ports ----------------------------------------------------------------------------------

    static final class InMemoryCards implements SpecCardStore {
        final Map<UUID, SpecCard> cards = new LinkedHashMap<>();

        public Optional<SpecCard> findById(UUID id) {
            return Optional.ofNullable(cards.get(id));
        }

        public Optional<SpecCard> findFirstByContentHash(String hash) {
            return cards.values().stream()
                    .filter(c -> hash.equals(c.getContentHash()))
                    .findFirst();
        }

        public Optional<SpecCard> findFirstBySourceUrl(String url) {
            return cards.values().stream()
                    .filter(c -> url.equals(c.getSourceUrl()))
                    .findFirst();
        }

        public List<SpecCard> findAllNewestFirst() {
            List<SpecCard> all = new ArrayList<>(cards.values());
            all.sort(Comparator.comparing(SpecCard::getCreatedAt).reversed());
            return all;
        }

        public List<SpecCard> findAll() {
            return List.copyOf(cards.values());
        }

        public boolean exists(UUID id) {
            return cards.containsKey(id);
        }

        public Set<UUID> allIds() {
            return Set.copyOf(cards.keySet());
        }

        public void save(SpecCard card) {
            cards.put(card.getId(), card);
        }

        public void delete(UUID id) {
            cards.remove(id);
        }
    }

    static final class FakeLinks implements CardLinkPort {
        final List<UUID> unlinked = new ArrayList<>();

        public Optional<UUID> linkedMicroservice(UUID cardId) {
            return Optional.empty();
        }

        public Map<UUID, UUID> linksByCard() {
            return new HashMap<>();
        }

        public void unlinkCard(UUID cardId) {
            unlinked.add(cardId);
        }
    }

    static final class NoSource implements SpecSource {
        public URI validate(String url) {
            throw new UnsupportedOperationException();
        }

        public String fetch(URI url) {
            throw new UnsupportedOperationException();
        }
    }

    /** Accepts anything except the text "broken". */
    static final class FakeParser implements SpecParserPort {
        public ParsedSpecification parse(String content, URI base, String serviceKey) {
            if (content.equals("broken")) {
                throw new SpecInvalidException("paths", "broken");
            }
            return new ParsedSpecification("Fake API", "1.0", SpecFormat.OPENAPI_3_0, List.of());
        }
    }
}
