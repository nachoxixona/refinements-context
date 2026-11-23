package com.refinements.visualizer.microservice.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.refinements.visualizer.microservice.application.port.in.MicroserviceUseCases;
import com.refinements.visualizer.shared.PatchBody;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Partial update (contracts/openapi.yaml MicroservicePatch). {@code descriptionPresent} distinguishes an absent
 * {@code description} (unchanged) from an explicit null (cleared).
 */
@Schema(name = "MicroservicePatch")
public record MicroservicePatch(
        @Schema(minLength = 1, maxLength = 120) String name,
        @Schema(maxLength = 2000, nullable = true) String description,
        @Schema(maxLength = 120, nullable = true) String team,
        @Schema(maxLength = 2048, nullable = true) String repositoryUrl,
        @Schema(maxLength = 2048, nullable = true) String docsUrl,
        @Schema(hidden = true) boolean descriptionPresent,
        @Schema(hidden = true) boolean teamPresent,
        @Schema(hidden = true) boolean repositoryUrlPresent,
        @Schema(hidden = true) boolean docsUrlPresent) {

    public MicroservicePatch(String name, String description, boolean descriptionPresent) {
        this(name, description, null, null, null, descriptionPresent, false, false, false);
    }

    public static MicroservicePatch from(JsonNode body) {
        PatchBody p = PatchBody.of(body, "name", "description", "team", "repositoryUrl", "docsUrl");
        return new MicroservicePatch(
                p.text("name"),
                p.text("description"),
                p.text("team"),
                p.text("repositoryUrl"),
                p.text("docsUrl"),
                p.has("description"),
                p.has("team"),
                p.has("repositoryUrl"),
                p.has("docsUrl"));
    }

    MicroserviceUseCases.MicroserviceChanges toChanges() {
        return new MicroserviceUseCases.MicroserviceChanges(
                name,
                description,
                descriptionPresent,
                team,
                teamPresent,
                repositoryUrl,
                repositoryUrlPresent,
                docsUrl,
                docsUrlPresent);
    }
}
