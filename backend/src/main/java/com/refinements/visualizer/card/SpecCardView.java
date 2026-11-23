package com.refinements.visualizer.card;

import com.refinements.visualizer.card.model.ParsedEndpoint;
import com.refinements.visualizer.card.model.SpecFormat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only view of a spec card for other modules (contracts/module-apis.md §card). */
public record SpecCardView(
        UUID id,
        String title,
        String apiVersion,
        SpecFormat specFormat,
        SourceType sourceType,
        String sourceUrl,
        String contentHash,
        Instant lastSyncAt,
        SyncStatus lastSyncStatus,
        List<ParsedEndpoint> endpoints,
        Instant createdAt) {

    static SpecCardView from(SpecCard c) {
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
