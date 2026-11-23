package com.refinements.visualizer.note.adapter.out.persistence;

import com.refinements.visualizer.note.NoteKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Row of the {@code note} table (V1__schema.sql). */
@Entity
@Table(name = "note")
class NoteEntity {

    @Id
    UUID id;

    @Column(name = "text", nullable = false, length = 2000)
    String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    NoteKind kind;

    @Column(name = "x", nullable = false)
    double x;

    @Column(name = "y", nullable = false)
    double y;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected NoteEntity() {}

    NoteEntity(UUID id) {
        this.id = id;
    }
}
