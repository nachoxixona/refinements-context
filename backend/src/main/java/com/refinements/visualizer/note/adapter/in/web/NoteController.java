package com.refinements.visualizer.note.adapter.in.web;

import com.refinements.visualizer.note.NoteDto;
import com.refinements.visualizer.note.application.port.in.CreateNote;
import com.refinements.visualizer.note.application.port.in.DeleteNote;
import com.refinements.visualizer.note.application.port.in.ListNotes;
import com.refinements.visualizer.note.application.port.in.UpdateNote;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code /api/notes} (spec 004 contracts/openapi.yaml). */
@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final ListNotes listNotes;
    private final CreateNote createNote;
    private final UpdateNote updateNote;
    private final DeleteNote deleteNote;

    NoteController(ListNotes listNotes, CreateNote createNote, UpdateNote updateNote, DeleteNote deleteNote) {
        this.listNotes = listNotes;
        this.createNote = createNote;
        this.updateNote = updateNote;
        this.deleteNote = deleteNote;
    }

    @GetMapping
    public List<NoteDto> list() {
        return listNotes.list();
    }

    @PostMapping
    public ResponseEntity<NoteDto> create(@Valid @RequestBody NoteWrite.Create write) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(createNote.create(write.text(), write.kind(), write.position()));
    }

    @PatchMapping("/{noteId}")
    public NoteDto update(@PathVariable UUID noteId, @Valid @RequestBody NoteWrite patch) {
        return updateNote.update(noteId, new UpdateNote.NoteChanges(patch.text(), patch.kind(), patch.position()));
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> delete(@PathVariable UUID noteId) {
        deleteNote.delete(noteId);
        return ResponseEntity.noContent().build();
    }
}
