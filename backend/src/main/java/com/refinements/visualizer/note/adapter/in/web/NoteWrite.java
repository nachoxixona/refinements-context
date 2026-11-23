package com.refinements.visualizer.note.adapter.in.web;

import com.refinements.visualizer.note.NoteKind;
import com.refinements.visualizer.shared.XYPosition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Create, or partial update: absent (null) properties are unchanged on PATCH. */
@Schema(name = "NoteWrite")
public record NoteWrite(
        @Schema(minLength = 1, maxLength = 2000) String text, NoteKind kind, @Valid XYPosition position) {

    /** For create: text and position are required, kind defaults to NOTE. */
    @Schema(name = "NoteCreate")
    public record Create(@NotNull String text, NoteKind kind, @NotNull @Valid XYPosition position) {}
}
