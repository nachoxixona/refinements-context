package com.refinements.visualizer.note.application;

import com.refinements.visualizer.note.NoteApi;
import com.refinements.visualizer.note.NoteDto;
import com.refinements.visualizer.note.NoteKind;
import com.refinements.visualizer.note.application.port.in.CreateNote;
import com.refinements.visualizer.note.application.port.in.DeleteNote;
import com.refinements.visualizer.note.application.port.in.ListNotes;
import com.refinements.visualizer.note.application.port.in.UpdateNote;
import com.refinements.visualizer.note.application.port.out.NoteStore;
import com.refinements.visualizer.note.domain.Note;
import com.refinements.visualizer.shared.XYPosition;
import com.refinements.visualizer.shared.errors.NotFoundException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Baseline notes (spec 004 FR-018, FR-019). Notes are not part of the connections document, so they don't bump the
 * workspace revision (research R5).
 */
@Service
class NoteService implements NoteApi, ListNotes, CreateNote, UpdateNote, DeleteNote {

    private final NoteStore notes;
    private final Clock clock;

    NoteService(NoteStore notes, Clock clock) {
        this.notes = notes;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoteDto> list() {
        return notes.findAllOldestFirst().stream().map(NoteService::toDto).toList();
    }

    @Override
    @Transactional
    public NoteDto create(String text, NoteKind kind, XYPosition position) {
        Note n = new Note(Note.checkText(text), kind == null ? NoteKind.NOTE : kind, position, clock.instant());
        return toDto(notes.save(n));
    }

    @Override
    @Transactional
    public NoteDto update(UUID id, NoteChanges changes) {
        Note n = find(id);
        n.change(
                changes.text() == null ? n.getText() : Note.checkText(changes.text()),
                changes.kind() == null ? n.getKind() : changes.kind(),
                changes.position() == null ? n.getPosition() : changes.position(),
                clock.instant());
        return toDto(notes.save(n));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        notes.delete(find(id));
    }

    private Note find(UUID id) {
        return notes.findById(id).orElseThrow(() -> new NotFoundException("No note " + id + "."));
    }

    private static NoteDto toDto(Note n) {
        return new NoteDto(n.getId(), n.getText(), n.getKind(), n.getPosition());
    }
}
