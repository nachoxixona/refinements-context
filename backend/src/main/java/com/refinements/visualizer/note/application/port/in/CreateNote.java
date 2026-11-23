package com.refinements.visualizer.note.application.port.in;

import com.refinements.visualizer.note.NoteDto;
import com.refinements.visualizer.note.NoteKind;
import com.refinements.visualizer.shared.XYPosition;

public interface CreateNote {

    /** Creates a note; {@code kind} defaults to NOTE. */
    NoteDto create(String text, NoteKind kind, XYPosition position);
}
