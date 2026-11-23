package com.refinements.visualizer.connection.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.refinements.visualizer.connection.ConnectionKind;
import com.refinements.visualizer.shared.PatchBody;
import com.refinements.visualizer.shared.errors.BadRequestException;
import io.swagger.v3.oas.annotations.media.Schema;

/** Partial update (contracts/openapi.yaml ConnectionPatch): absent leaves a property unchanged, null clears it. */
@Schema(name = "ConnectionPatch")
public record ConnectionPatch(
        @Schema(minLength = 1, maxLength = 120) String name,
        @Schema(nullable = true) ConnectionKind kind,
        @Schema(maxLength = 2000, nullable = true) String description,
        @Schema(nullable = true, format = "uuid") String flowId,
        @Schema(nullable = true, maxLength = 600) String endpoint,
        @Schema(nullable = true, minimum = "1") Integer step,
        @Schema(hidden = true) boolean kindPresent,
        @Schema(hidden = true) boolean descriptionPresent,
        @Schema(hidden = true) boolean flowPresent,
        @Schema(hidden = true) boolean endpointPresent,
        @Schema(hidden = true) boolean stepPresent) {

    public static ConnectionPatch from(JsonNode body) {
        PatchBody p = PatchBody.of(body, "name", "kind", "description", "flowId", "endpoint", "step");
        Integer step = null;
        if (p.has("step") && !body.get("step").isNull()) {
            if (!body.get("step").canConvertToInt() || !body.get("step").isIntegralNumber()) {
                throw new BadRequestException("step must be a whole number or null.");
            }
            step = body.get("step").asInt();
        }
        String kindText = p.text("kind");
        ConnectionKind kind = null;
        if (kindText != null) {
            try {
                kind = ConnectionKind.valueOf(kindText);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("kind must be REQUEST, EVENT or null.");
            }
        }
        if (p.has("name") && p.text("name") == null) {
            throw new BadRequestException("name cannot be null.");
        }
        String flow = p.text("flowId");
        if (flow != null) {
            try {
                java.util.UUID.fromString(flow);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("flowId must be a UUID or null.");
            }
        }
        return new ConnectionPatch(
                p.text("name"),
                kind,
                p.text("description"),
                flow,
                p.text("endpoint"),
                step,
                p.has("kind"),
                p.has("description"),
                p.has("flowId"),
                p.has("endpoint"),
                p.has("step"));
    }
}
