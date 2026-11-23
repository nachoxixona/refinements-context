package com.refinements.visualizer.note;

import com.refinements.visualizer.shared.XYPosition;
import java.util.UUID;

/** A sticky note as other modules see it (contracts/module-apis.md §note). */
public record NoteView(UUID id, String text, NoteKind kind, XYPosition position) {}
