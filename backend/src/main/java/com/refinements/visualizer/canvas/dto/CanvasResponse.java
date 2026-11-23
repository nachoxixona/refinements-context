package com.refinements.visualizer.canvas.dto;

import com.refinements.visualizer.connection.ConnectionView;
import com.refinements.visualizer.connection.FlowView;
import com.refinements.visualizer.note.NoteView;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "CanvasResponse")
public record CanvasResponse(
        List<CanvasNode> nodes,
        List<CanvasEdge> edges,
        // the published shapes are the connection module's REST schemas "Connection" and "Flow" (same fields)
        @ArraySchema(schema = @Schema(ref = "#/components/schemas/Connection"))
                List<ConnectionView> danglingConnections,
        @ArraySchema(schema = @Schema(ref = "#/components/schemas/Flow")) List<FlowView> flows,
        // the published shape is the note module's REST schema "Note" (same fields as NoteView)
        @ArraySchema(schema = @Schema(ref = "#/components/schemas/Note")) List<NoteView> notes,
        long revision,
        int layoutVersion) {}
