package com.refinements.visualizer.note.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.refinements.visualizer.note.NoteKind;
import com.refinements.visualizer.shared.XYPosition;
import com.refinements.visualizer.shared.errors.BadRequestException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The note rules with no Spring and no database. */
class NoteDomainTest {

    private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-23T11:00:00Z");

    @Test
    void newNotesDefaultToKindNote() {
        Note n = Note.create("Hello", null, new XYPosition(1.0, 2.0), T0);
        assertThat(n.getKind()).isEqualTo(NoteKind.NOTE);
        assertThat(n.getCreatedAt()).isEqualTo(T0);
    }

    @Test
    void partialChangesKeepAbsentProperties() {
        Note n = Note.create("Hello", NoteKind.DECISION, new XYPosition(1.0, 2.0), T0);
        n.change(null, null, new XYPosition(5.0, 6.0), T1);
        assertThat(n.getText()).isEqualTo("Hello");
        assertThat(n.getKind()).isEqualTo(NoteKind.DECISION);
        assertThat(n.getPosition()).isEqualTo(new XYPosition(5.0, 6.0));
        assertThat(n.getUpdatedAt()).isEqualTo(T1);
    }

    @Test
    void textMustBeNonBlankAndAtMost2000Characters() {
        XYPosition p = new XYPosition(0.0, 0.0);
        assertThatThrownBy(() -> Note.create(" ", null, p, T0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Note.create("x".repeat(2001), null, p, T0)).isInstanceOf(BadRequestException.class);
        Note n = Note.create("x".repeat(2000), null, p, T0);
        assertThatThrownBy(() -> n.change("", null, null, T1)).isInstanceOf(BadRequestException.class);
    }
}
