package com.refinements.visualizer.card.adapter.in.web;

import com.refinements.visualizer.card.SourceType;
import com.refinements.visualizer.card.SpecCardView;
import com.refinements.visualizer.card.SyncStatus;
import com.refinements.visualizer.card.application.port.in.SpecCardUseCases;
import com.refinements.visualizer.card.model.SpecFormat;
import com.refinements.visualizer.shared.errors.ParseError;
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

    static SpecCardSummary from(SpecCardUseCases.CardResult r) {
        SpecCardView c = r.card();
        return new SpecCardSummary(
                c.id(),
                c.title(),
                c.apiVersion(),
                c.specFormat(),
                c.sourceType(),
                c.sourceUrl(),
                c.contentHash(),
                c.endpoints().size(),
                c.lastSyncStatus(),
                c.lastSyncAt(),
                r.lastSyncErrors(),
                r.unchanged(),
                r.linkedMicroserviceId());
    }
}
