package com.refinements.visualizer.layout.domain;

import com.refinements.visualizer.shared.XYPosition;
import com.refinements.visualizer.shared.errors.StaleVersionException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The single baseline layout (data-model.md §5): BaselineLayout.positions keyed {@code ms:{id}} / {@code card:{id}},
 * BaselineLayout.collapsedCards, and BaselineLayout.version as the optimistic lock. Plain Java.
 */
public class BaselineLayout {

    private Map<String, XYPosition> positions;
    private List<UUID> collapsedCards;
    private final int version;
    private Instant updatedAt;

    public BaselineLayout(
            Map<String, XYPosition> positions, List<UUID> collapsedCards, int version, Instant updatedAt) {
        this.positions = new LinkedHashMap<>(positions);
        this.collapsedCards = new ArrayList<>(collapsedCards);
        this.version = version;
        this.updatedAt = updatedAt;
    }

    /** Throws 409 when the client edited an older version (FR-035). */
    public void checkVersion(int expected) {
        if (version != expected) {
            throw new StaleVersionException(
                    "The layout changed since it was loaded (for example in another tab). Reload and try again.");
        }
    }

    /**
     * Replaces positions and collapsed cards, keeping only keys in {@code knownKeys} and cards in {@code knownCards}
     * (keys of deleted entities are pruned; docked cards have no key of their own, research R8 of 003).
     */
    public void replace(
            Map<String, XYPosition> newPositions,
            List<UUID> newCollapsed,
            Set<String> knownKeys,
            Set<UUID> knownCards,
            Instant now) {
        Map<String, XYPosition> pruned = new LinkedHashMap<>();
        newPositions.forEach((key, pos) -> {
            if (knownKeys.contains(key)) {
                pruned.put(key, pos);
            }
        });
        Set<UUID> collapsed = new LinkedHashSet<>();
        for (UUID id : newCollapsed) {
            if (knownCards.contains(id)) {
                collapsed.add(id);
            }
        }
        this.positions = pruned;
        this.collapsedCards = new ArrayList<>(collapsed);
        this.updatedAt = now;
    }

    public Map<String, XYPosition> getPositions() {
        return Collections.unmodifiableMap(positions);
    }

    public List<UUID> getCollapsedCards() {
        return Collections.unmodifiableList(collapsedCards);
    }

    public int getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
