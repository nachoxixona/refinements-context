package com.refinements.visualizer.note.adapter.out.persistence;

import com.refinements.visualizer.note.domain.Note;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NoteJpaRepository extends JpaRepository<Note, UUID> {

    List<Note> findAllByOrderByCreatedAtAscIdAsc();
}
