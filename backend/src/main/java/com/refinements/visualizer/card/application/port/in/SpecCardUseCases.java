package com.refinements.visualizer.card.application.port.in;

import com.refinements.visualizer.card.SourceType;
import com.refinements.visualizer.card.SpecCardView;
import com.refinements.visualizer.shared.errors.ParseError;
import java.util.List;
import java.util.UUID;

/** What the card module offers its inbound adapters (FR-001 – FR-009). */
public interface SpecCardUseCases {

    /** Import a spec: PASTE needs {@code content}, URL needs {@code url}. */
    record ImportCard(SourceType sourceType, String content, String url) {}

    /** A card with its last sync errors, whether a re-sync changed nothing, and its linked microservice. */
    record CardResult(
            SpecCardView card, List<ParseError> lastSyncErrors, boolean unchanged, UUID linkedMicroserviceId) {}

    /** All cards, newest first. */
    List<CardResult> list();

    /** Imports a spec as a new, unlinked card. Duplicates by content hash or source URL → 409. */
    CardResult importCard(ImportCard command);

    /** Re-syncs from the source URL, or from {@code newContent} (required for pasted cards). */
    CardResult sync(UUID cardId, String newContent);

    /** Deletes the card; its microservice (if any) becomes spec-less. Connections are untouched. */
    void delete(UUID cardId);
}
