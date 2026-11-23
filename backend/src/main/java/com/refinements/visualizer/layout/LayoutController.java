package com.refinements.visualizer.layout;

import com.refinements.visualizer.card.SpecCardRepository;
import com.refinements.visualizer.common.XYPosition;
import com.refinements.visualizer.common.errors.StaleVersionException;
import com.refinements.visualizer.microservice.Microservice;
import com.refinements.visualizer.microservice.MicroserviceRepository;
import jakarta.validation.Valid;
import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code PUT /api/layout}: replaces positions and collapsed cards with optimistic locking (FR-014, FR-035).
 * Keys of deleted entities are pruned; docked cards keep no position of their own (research R8).
 */
@RestController
@RequestMapping("/api/layout")
public class LayoutController {

    public record LayoutVersion(int version) {}

    private final BaselineLayoutRepository layouts;
    private final MicroserviceRepository microservices;
    private final SpecCardRepository cards;
    private final Clock clock;

    public LayoutController(
            BaselineLayoutRepository layouts,
            MicroserviceRepository microservices,
            SpecCardRepository cards,
            Clock clock) {
        this.layouts = layouts;
        this.microservices = microservices;
        this.cards = cards;
        this.clock = clock;
    }

    @PutMapping
    @Transactional
    public LayoutVersion save(@Valid @RequestBody LayoutUpdate update) {
        BaselineLayout layout = layouts.getSingleton();
        if (layout.getVersion() != update.version()) {
            throw new StaleVersionException(
                    "The layout changed since it was loaded (for example in another tab). Reload and try again.");
        }
        Set<String> keys = new HashSet<>();
        Set<UUID> docked = new HashSet<>();
        for (Microservice m : microservices.findAll()) {
            keys.add(BaselineLayout.msKey(m.getId()));
            if (m.getSpecCardId() != null) {
                docked.add(m.getSpecCardId());
            }
        }
        Set<UUID> cardIds = new HashSet<>(cards.findAllIds());
        for (UUID id : cardIds) {
            if (!docked.contains(id)) {
                keys.add(BaselineLayout.cardKey(id));
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
        layout.replace(pruned, java.util.List.copyOf(collapsed), clock.instant());
        BaselineLayout saved = layouts.saveAndFlush(layout);
        return new LayoutVersion(saved.getVersion());
    }
}
