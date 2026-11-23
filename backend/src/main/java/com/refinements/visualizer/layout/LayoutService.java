package com.refinements.visualizer.layout;

import com.refinements.visualizer.card.SpecCardApi;
import com.refinements.visualizer.microservice.MicroserviceApi;
import com.refinements.visualizer.microservice.MicroserviceView;
import com.refinements.visualizer.shared.XYPosition;
import com.refinements.visualizer.shared.errors.StaleVersionException;
import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves positions and collapsed cards with optimistic locking (FR-014, FR-035). Keys of deleted entities are pruned;
 * docked cards keep no position of their own (research R8).
 */
@Service
public class LayoutService implements LayoutApi {

    private final BaselineLayoutRepository layouts;
    private final MicroserviceApi microservices;
    private final SpecCardApi cards;
    private final Clock clock;

    public LayoutService(
            BaselineLayoutRepository layouts, MicroserviceApi microservices, SpecCardApi cards, Clock clock) {
        this.layouts = layouts;
        this.microservices = microservices;
        this.cards = cards;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public LayoutView current() {
        return LayoutView.from(layouts.getSingleton());
    }

    @Transactional
    public int save(LayoutUpdate update) {
        BaselineLayout layout = layouts.getSingleton();
        if (layout.getVersion() != update.version()) {
            throw new StaleVersionException(
                    "The layout changed since it was loaded (for example in another tab). Reload and try again.");
        }
        Set<String> keys = new HashSet<>();
        Set<UUID> docked = new HashSet<>();
        for (MicroserviceView m : microservices.findAll()) {
            keys.add(LayoutKeys.msKey(m.id()));
            if (m.specCardId() != null) {
                docked.add(m.specCardId());
            }
        }
        Set<UUID> cardIds = new HashSet<>(cards.allIds());
        for (UUID id : cardIds) {
            if (!docked.contains(id)) {
                keys.add(LayoutKeys.cardKey(id));
            }
        }
        Map<String, XYPosition> pruned = new LinkedHashMap<>();
        update.positions().forEach((key, pos) -> {
            if (keys.contains(key)) {
                pruned.put(key, pos);
            }
        });
        Set<UUID> collapsed = new LinkedHashSet<>();
        for (UUID id : update.collapsedCards()) {
            if (cardIds.contains(id)) {
                collapsed.add(id);
            }
        }
        layout.replace(pruned, List.copyOf(collapsed), clock.instant());
        BaselineLayout saved = layouts.saveAndFlush(layout);
        return saved.getVersion();
    }
}
