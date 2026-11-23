package com.refinements.visualizer.note.application.port.out;

import com.refinements.visualizer.note.domain.Note;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Where notes are kept. */
public interface NoteStore {

    /** All notes, oldest first (createdAt, then id). */
    List<Note> findAllOldestFirst();

    Optional<Note> findById(UUID id);

    /** Saves and flushes; returns the managed note. */
    Note save(Note note);

    /** Deletes and flushes. */
    void delete(Note note);
}
