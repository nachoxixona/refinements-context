package com.refinements.visualizer.note.application.port.in;

import com.refinements.visualizer.note.NoteDto;
import com.refinements.visualizer.note.NoteKind;
import com.refinements.visualizer.shared.XYPosition;
import java.util.UUID;

public interface UpdateNote {

    /** A partial change: {@code null} properties are left as they are. */
    record NoteChanges(String text, NoteKind kind, XYPosition position) {}

    NoteDto update(UUID id, NoteChanges changes);
}
