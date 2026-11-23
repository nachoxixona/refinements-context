package com.refinements.visualizer.note;

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

    private final NoteService service;

    public NoteController(NoteService service) {
        this.service = service;
    }

    @GetMapping
    public List<NoteDto> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<NoteDto> create(@Valid @RequestBody NoteWrite.Create write) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(write.text(), write.kind(), write.position()));
    }

    @PatchMapping("/{noteId}")
    public NoteDto update(@PathVariable UUID noteId, @Valid @RequestBody NoteWrite patch) {
        return service.update(noteId, patch);
    }

    @DeleteMapping("/{noteId}")
    public ResponseEntity<Void> delete(@PathVariable UUID noteId) {
        service.delete(noteId);
        return ResponseEntity.noContent().build();
    }
}
