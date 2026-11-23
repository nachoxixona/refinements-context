package com.refinements.visualizer.canvas.domain;

import com.refinements.visualizer.card.SourceType;
import com.refinements.visualizer.card.SyncStatus;
import com.refinements.visualizer.card.model.SpecFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Card payload. {@code contentHash}, {@code sourceUrl} and {@code lastSyncAt} trace it to the spec upload. */
@Schema(name = "SpecCardNodeData")
public record SpecCardNodeData(
        UUID cardId,
        String title,
        String apiVersion,
        SpecFormat specFormat,
        SourceType sourceType,
        String sourceUrl,
        String contentHash,
        Instant lastSyncAt,
        SyncStatus lastSyncStatus,
        List<EndpointDto> endpoints,
        UUID linkedMicroserviceId) {}
