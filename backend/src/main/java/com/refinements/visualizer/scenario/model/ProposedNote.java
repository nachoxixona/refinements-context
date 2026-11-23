package com.refinements.visualizer.scenario.model;

import com.refinements.visualizer.note.NoteKind;
import com.refinements.visualizer.shared.XYPosition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A note added in a draft (spec 004 data-model.md §4 ScenarioContent.notes, FR-020). */
@Schema(name = "ProposedNote")
public record ProposedNote(
        @NotBlank String tempKey,
        @NotBlank @Size(max = 2000) String text,
        @NotNull NoteKind kind,
        @NotNull @Valid XYPosition position) {}
