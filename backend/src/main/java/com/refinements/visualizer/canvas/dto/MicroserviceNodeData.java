package com.refinements.visualizer.canvas.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "MicroserviceNodeData")
public record MicroserviceNodeData(
        UUID microserviceId,
        String name,
        String description,
        UUID specCardId,
        String cardTitle,
        boolean cardCollapsed,
        String team,
        String repositoryUrl,
        String docsUrl) {}
