package com.refinements.visualizer.note;

import com.refinements.visualizer.common.XYPosition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A sticky note on the baseline canvas (spec 004 data-model.md §3 Note.text, Note.kind, Note.position). */
@Entity
@Table(name = "note")
public class Note {

    public static final int MAX_TEXT = 2000;

    @Id
    private UUID id;

    @Column(name = "text", nullable = false, length = MAX_TEXT)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private NoteKind kind;

    @Column(name = "x", nullable = false)
    private double x;

    @Column(name = "y", nullable = false)
    private double y;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Note() {}

    public Note(String text, NoteKind kind, XYPosition position, Instant now) {
        this.id = UUID.randomUUID();
        this.createdAt = now;
        change(text, kind, position, now);
    }

    public final void change(String text, NoteKind kind, XYPosition position, Instant now) {
        this.text = text;
        this.kind = kind;
        this.x = position.x();
        this.y = position.y();
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public NoteKind getKind() {
        return kind;
    }

    public XYPosition getPosition() {
        return new XYPosition(x, y);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
