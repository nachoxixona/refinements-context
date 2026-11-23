package com.refinements.visualizer.note.application.port.in;

import com.refinements.visualizer.note.NoteKind;
import com.refinements.visualizer.note.NoteView;
import com.refinements.visualizer.shared.XYPosition;
import java.util.List;
import java.util.UUID;

/** What the note module offers its inbound adapters. */
public interface NoteUseCases {

    /** A partial change: {@code null} properties are left as they are. */
    record NoteChanges(String text, NoteKind kind, XYPosition position) {}

    /** All notes, oldest first. */
    List<NoteView> list();

    /** Creates a note; {@code kind} defaults to NOTE. */
    NoteView create(String text, NoteKind kind, XYPosition position);

    NoteView update(UUID id, NoteChanges changes);

    void delete(UUID id);
}
