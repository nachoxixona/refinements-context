package com.refinements.visualizer.note.adapter.out.persistence;

import com.refinements.visualizer.note.application.port.out.NoteStore;
import com.refinements.visualizer.note.domain.Note;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** {@link NoteStore} on Spring Data JPA. */
@Component
class NoteStoreAdapter implements NoteStore {

    private final NoteJpaRepository repository;

    NoteStoreAdapter(NoteJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Note> findAllOldestFirst() {
        return repository.findAllByOrderByCreatedAtAscIdAsc();
    }

    @Override
    public Optional<Note> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Note save(Note note) {
        return repository.saveAndFlush(note);
    }

    @Override
    public void delete(Note note) {
        repository.delete(note);
        repository.flush();
    }
}
