package com.refinements.visualizer.note.application;

import com.refinements.visualizer.note.NoteApi;
import com.refinements.visualizer.note.NoteKind;
import com.refinements.visualizer.note.NoteView;
import com.refinements.visualizer.note.application.port.in.NoteUseCases;
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
class NoteService implements NoteApi, NoteUseCases {

    private final NoteStore notes;
    private final Clock clock;

    NoteService(NoteStore notes, Clock clock) {
        this.notes = notes;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoteView> list() {
        return notes.findAllOldestFirst().stream().map(NoteService::view).toList();
    }

    @Override
    @Transactional
    public NoteView create(String text, NoteKind kind, XYPosition position) {
        Note n = Note.create(text, kind, position, clock.instant());
        notes.save(n);
        return view(n);
    }

    @Override
    @Transactional
    public NoteView update(UUID id, NoteChanges changes) {
        Note n = find(id);
        n.change(changes.text(), changes.kind(), changes.position(), clock.instant());
        notes.save(n);
        return view(n);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        notes.delete(find(id).getId());
    }

    private Note find(UUID id) {
        return notes.findById(id).orElseThrow(() -> new NotFoundException("No note " + id + "."));
    }

    private static NoteView view(Note n) {
        return new NoteView(n.getId(), n.getText(), n.getKind(), n.getPosition());
    }
}
