package com.refinements.visualizer.note.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NoteJpaRepository extends JpaRepository<NoteEntity, UUID> {

    List<NoteEntity> findAllByOrderByCreatedAtAscIdAsc();
}
