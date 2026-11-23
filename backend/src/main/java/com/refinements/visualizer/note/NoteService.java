package com.refinements.visualizer.note;

import com.refinements.visualizer.common.XYPosition;
import com.refinements.visualizer.common.errors.BadRequestException;
import com.refinements.visualizer.common.errors.NotFoundException;
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
public class NoteService {

    private final NoteRepository notes;
    private final Clock clock;

    public NoteService(NoteRepository notes, Clock clock) {
        this.notes = notes;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NoteDto> list() {
        return notes.findAllByOrderByCreatedAtAscIdAsc().stream()
                .map(NoteDto::from)
                .toList();
    }

    @Transactional
    public NoteDto create(String text, NoteKind kind, XYPosition position) {
        Note n = new Note(checkText(text), kind == null ? NoteKind.NOTE : kind, position, clock.instant());
        return NoteDto.from(notes.saveAndFlush(n));
    }

    @Transactional
    public NoteDto update(UUID id, NoteWrite patch) {
        Note n = notes.findById(id).orElseThrow(() -> new NotFoundException("No note " + id + "."));
        n.change(
                patch.text() == null ? n.getText() : checkText(patch.text()),
                patch.kind() == null ? n.getKind() : patch.kind(),
                patch.position() == null ? n.getPosition() : patch.position(),
                clock.instant());
        return NoteDto.from(notes.saveAndFlush(n));
    }

    @Transactional
    public void delete(UUID id) {
        notes.delete(notes.findById(id).orElseThrow(() -> new NotFoundException("No note " + id + ".")));
        notes.flush();
    }

    static String checkText(String text) {
        if (text == null || text.isBlank()) {
            throw new BadRequestException("A note needs some text.");
        }
        if (text.length() > Note.MAX_TEXT) {
            throw new BadRequestException("Notes are at most " + Note.MAX_TEXT + " characters.");
        }
        return text;
    }
}
