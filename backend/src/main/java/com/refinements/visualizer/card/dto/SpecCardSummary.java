package com.refinements.visualizer.card.dto;

import com.refinements.visualizer.card.SourceType;
import com.refinements.visualizer.card.SpecCard;
import com.refinements.visualizer.card.SyncStatus;
import com.refinements.visualizer.common.errors.ParseError;
import com.refinements.visualizer.ingestion.model.SpecFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(name = "SpecCardSummary")
public record SpecCardSummary(
        UUID id,
        String title,
        String apiVersion,
        SpecFormat specFormat,
        SourceType sourceType,
        String sourceUrl,
        String contentHash,
        int endpointCount,
        SyncStatus lastSyncStatus,
        Instant lastSyncAt,
        List<ParseError> lastSyncErrors,
        boolean unchanged,
        UUID linkedMicroserviceId) {

    public static SpecCardSummary from(SpecCard c, boolean unchanged, UUID linkedMicroserviceId) {
        return new SpecCardSummary(
                c.getId(),
                c.getTitle(),
                c.getApiVersion(),
                c.getSpecFormat(),
                c.getSourceType(),
                c.getSourceUrl(),
                c.getContentHash(),
                c.getEndpoints().size(),
                c.getLastSyncStatus(),
                c.getLastSyncAt(),
                List.copyOf(c.getLastSyncErrors()),
                unchanged,
                linkedMicroserviceId);
    }
}
