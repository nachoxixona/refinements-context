package com.refinements.visualizer.card.adapter.out.persistence;

import com.refinements.visualizer.card.domain.SpecCard;
import java.util.ArrayList;

/** SpecCard ↔ SpecCardEntity. */
final class SpecCardMapper {

    private SpecCardMapper() {}

    static SpecCard toDomain(SpecCardEntity e) {
        return SpecCard.restore(
                e.id,
                e.title,
                e.apiVersion,
                e.specFormat,
                e.sourceType,
                e.sourceUrl,
                e.rawContent,
                e.contentHash,
                e.endpoints,
                e.lastSyncAt,
                e.lastSyncStatus,
                e.lastSyncErrors,
                e.createdAt,
                e.updatedAt);
    }

    /** Copies the card's state onto a (new or loaded) entity. */
    static void copy(SpecCard c, SpecCardEntity e) {
        e.title = c.getTitle();
        e.apiVersion = c.getApiVersion();
        e.specFormat = c.getSpecFormat();
        e.sourceType = c.getSourceType();
        e.sourceUrl = c.getSourceUrl();
        e.rawContent = c.getRawContent();
        e.contentHash = c.getContentHash();
        e.endpoints = new ArrayList<>(c.getEndpoints());
        e.lastSyncAt = c.getLastSyncAt();
        e.lastSyncStatus = c.getLastSyncStatus();
        e.lastSyncErrors = new ArrayList<>(c.getLastSyncErrors());
        e.createdAt = c.getCreatedAt();
        e.updatedAt = c.getUpdatedAt();
    }
}
