package com.refinements.visualizer.microservice;

import java.util.UUID;

/** Read-only view of a microservice for other modules (contracts/module-apis.md §microservice). */
public record MicroserviceView(
        UUID id,
        String name,
        String nameNormalized,
        String description,
        UUID specCardId,
        String team,
        String repositoryUrl,
        String docsUrl) {}
