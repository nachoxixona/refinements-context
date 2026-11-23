package com.refinements.visualizer.note.adapter.out.persistence;

import com.refinements.visualizer.note.application.port.out.NoteStore;
import com.refinements.visualizer.note.domain.Note;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link NoteStore} on Spring Data JPA. Saving copies the domain state onto the managed entity (or a new one), so
 * Hibernate dirty checking and flush order work as before (research R7).
 */
@Component
class NoteStoreAdapter implements NoteStore {

    private final NoteJpaRepository repository;

    NoteStoreAdapter(NoteJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Note> findAllOldestFirst() {
        return repository.findAllByOrderByCreatedAtAscIdAsc().stream()
                .map(NoteMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Note> findById(UUID id) {
        return repository.findById(id).map(NoteMapper::toDomain);
    }

    @Override
    public void save(Note note) {
        NoteEntity e = repository.findById(note.getId()).orElseGet(() -> new NoteEntity(note.getId()));
        NoteMapper.copy(note, e);
        repository.saveAndFlush(e);
    }

    @Override
    public void delete(UUID id) {
        repository.deleteById(id);
        repository.flush();
    }
}
