package com.refinements.visualizer.note.adapter.out.persistence;

import com.refinements.visualizer.note.domain.Note;
import com.refinements.visualizer.shared.XYPosition;

/** Note ↔ NoteEntity. */
final class NoteMapper {

    private NoteMapper() {}

    static Note toDomain(NoteEntity e) {
        return Note.restore(e.id, e.text, e.kind, new XYPosition(e.x, e.y), e.createdAt, e.updatedAt);
    }

    /** Copies the note's state onto a (new or managed) entity. */
    static void copy(Note n, NoteEntity e) {
        e.text = n.getText();
        e.kind = n.getKind();
        e.x = n.getPosition().x();
        e.y = n.getPosition().y();
        e.createdAt = n.getCreatedAt();
        e.updatedAt = n.getUpdatedAt();
    }
}
