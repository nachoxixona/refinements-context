package com.refinements.visualizer.note;

import com.refinements.visualizer.shared.XYPosition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "Note")
public record NoteDto(UUID id, String text, NoteKind kind, XYPosition position) {}
