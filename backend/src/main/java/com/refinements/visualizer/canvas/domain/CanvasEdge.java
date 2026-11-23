package com.refinements.visualizer.canvas.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** React Connection Edge for an attached connection. Parallel offsets and bundling are client-side (research R11). */
@Schema(name = "CanvasEdge")
public record CanvasEdge(String id, String source, String target, String type, ConnectionEdgeData data) {

    public static final String TYPE = "connection";
}
