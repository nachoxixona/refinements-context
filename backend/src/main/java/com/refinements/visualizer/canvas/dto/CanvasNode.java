package com.refinements.visualizer.canvas.dto;

import com.refinements.visualizer.common.XYPosition;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * React Connection Node: {@code ms:{uuid}} microservices and {@code card:{uuid}} cards. A docked card has
 * {@code parentId} set and a position relative to its microservice (research R8).
 */
@Schema(name = "CanvasNode")
public record CanvasNode(
        String id,
        String type,
        String parentId,
        boolean draggable,
        @Schema(description = "React Flow hidden: true for a docked card collapsed to its 'API' badge") boolean hidden,
        XYPosition position,
        @Schema(oneOf = {MicroserviceNodeData.class, SpecCardNodeData.class}) Object data) {

    public static final String MICROSERVICE = "microservice";
    public static final String SPEC_CARD = "specCard";
}
