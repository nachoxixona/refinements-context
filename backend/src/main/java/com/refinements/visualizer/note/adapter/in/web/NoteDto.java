package com.refinements.visualizer.note.adapter.in.web;

import com.refinements.visualizer.note.NoteKind;
import com.refinements.visualizer.note.NoteView;
import com.refinements.visualizer.shared.XYPosition;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "Note")
public record NoteDto(UUID id, String text, NoteKind kind, XYPosition position) {

    static NoteDto from(NoteView n) {
        return new NoteDto(n.id(), n.text(), n.kind(), n.position());
    }
}
