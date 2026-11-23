package com.refinements.visualizer.canvas.dto;

import com.refinements.visualizer.connection.ConnectionDto;
import com.refinements.visualizer.connection.FlowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "CanvasResponse")
public record CanvasResponse(
        List<CanvasNode> nodes,
        List<CanvasEdge> edges,
        List<ConnectionDto> danglingConnections,
        List<FlowDto> flows,
        List<com.refinements.visualizer.note.NoteDto> notes,
        long revision,
        int layoutVersion) {}
