package com.refinements.visualizer.card;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** What other modules may ask the card module (contracts/module-apis.md §card). */
public interface SpecCardApi {

    boolean exists(UUID cardId);

    /** All cards, oldest first (createdAt, then id). */
    List<SpecCardView> findAll();

    Set<UUID> allIds();
}
