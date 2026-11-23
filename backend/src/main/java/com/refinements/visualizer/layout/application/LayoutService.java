package com.refinements.visualizer.layout.application;

import com.refinements.visualizer.layout.LayoutApi;
import com.refinements.visualizer.layout.LayoutKeys;
import com.refinements.visualizer.layout.LayoutView;
import com.refinements.visualizer.layout.application.port.in.LayoutUseCases;
import com.refinements.visualizer.layout.application.port.out.LayoutStore;
import com.refinements.visualizer.layout.application.port.out.LayoutTargets;
import com.refinements.visualizer.layout.domain.BaselineLayout;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saves positions and collapsed cards with optimistic locking (FR-014, FR-035). */
@Service
class LayoutService implements LayoutApi, LayoutUseCases {

    private final LayoutStore layouts;
    private final LayoutTargets targets;
    private final Clock clock;

    LayoutService(LayoutStore layouts, LayoutTargets targets, Clock clock) {
        this.layouts = layouts;
        this.targets = targets;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public LayoutView current() {
        BaselineLayout l = layouts.get();
        return new LayoutView(l.getPositions(), List.copyOf(l.getCollapsedCards()), l.getVersion());
    }

    @Override
    @Transactional
    public int save(SaveLayout command) {
        BaselineLayout layout = layouts.get();
        layout.checkVersion(command.version());
        Set<String> keys = new HashSet<>();
        Set<UUID> docked = new HashSet<>();
        for (Map.Entry<UUID, UUID> m : targets.microserviceCards().entrySet()) {
            keys.add(LayoutKeys.msKey(m.getKey()));
            if (m.getValue() != null) {
                docked.add(m.getValue());
            }
        }
        Set<UUID> cardIds = targets.cardIds();
        for (UUID id : cardIds) {
            if (!docked.contains(id)) {
                keys.add(LayoutKeys.cardKey(id));
            }
        }
        layout.replace(command.positions(), command.collapsedCards(), keys, cardIds, clock.instant());
        return layouts.save(layout);
    }
}
