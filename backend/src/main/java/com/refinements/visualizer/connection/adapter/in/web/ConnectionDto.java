package com.refinements.visualizer.connection.adapter.in.web;

import com.refinements.visualizer.connection.ConnectionKind;
import com.refinements.visualizer.connection.ConnectionView;
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

    public static ConnectionDto from(ConnectionView v) {
        return new ConnectionDto(
                v.id(),
                v.name(),
                v.kind(),
                v.description(),
                v.flowId(),
                v.endpoint(),
                v.step(),
                v.sourceId(),
                v.targetId(),
                v.sourceName(),
                v.targetName(),
                v.dangling());
    }
}
