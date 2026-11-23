package com.refinements.visualizer.note.domain;

import com.refinements.visualizer.note.NoteKind;
import com.refinements.visualizer.shared.XYPosition;
import com.refinements.visualizer.shared.errors.BadRequestException;
import java.time.Instant;
import java.util.UUID;

/** A sticky note on the baseline canvas (spec 004 data-model.md §3 Note.text, Note.kind, Note.position). */
public class Note {

    public static final int MAX_TEXT = 2000;

    private final UUID id;
    private String text;
    private NoteKind kind;
    private XYPosition position;
    private final Instant createdAt;
    private Instant updatedAt;

    /** A new note; the text must pass {@link #checkText}. */
    public static Note create(String text, NoteKind kind, XYPosition position, Instant now) {
        return new Note(UUID.randomUUID(), checkText(text), kind == null ? NoteKind.NOTE : kind, position, now, now);
    }

    /** Rebuilds a stored note (persistence adapter only). */
    public static Note restore(
            UUID id, String text, NoteKind kind, XYPosition position, Instant createdAt, Instant updatedAt) {
        return new Note(id, text, kind, position, createdAt, updatedAt);
    }

    private Note(UUID id, String text, NoteKind kind, XYPosition position, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.text = text;
        this.kind = kind;
        this.position = position;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Applies a partial change: {@code null} arguments leave the property as it is. */
    public void change(String text, NoteKind kind, XYPosition position, Instant now) {
        this.text = text == null ? this.text : checkText(text);
        this.kind = kind == null ? this.kind : kind;
        this.position = position == null ? this.position : position;
        this.updatedAt = now;
    }

    /** Note.text rule (spec 004 data-model.md §3): 1–2000 characters, not blank. */
    static String checkText(String text) {
        if (text == null || text.isBlank()) {
            throw new BadRequestException("A note needs some text.");
        }
        if (text.length() > MAX_TEXT) {
            throw new BadRequestException("Notes are at most " + MAX_TEXT + " characters.");
        }
        return text;
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
        return position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
