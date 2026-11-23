package com.refinements.visualizer.connection;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "Connection")
public record ConnectionDto(
        UUID id,
        String name,
        ConnectionKind kind,
        String description,
        UUID flowId,
        String endpoint,
        Integer step,
        UUID sourceId,
        UUID targetId,
        String sourceName,
        String targetName,
        boolean dangling) {

    public static ConnectionDto from(Connection f) {
        return new ConnectionDto(
                f.getId(),
                f.getName(),
                f.getKind(),
                f.getDescription(),
                f.getFlowId(),
                f.getEndpoint(),
                f.getStep(),
                f.getSourceId(),
                f.getTargetId(),
                f.getSourceName(),
                f.getTargetName(),
                f.isDangling());
    }
}
