package com.refinements.visualizer.note;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.refinements.visualizer.note.application.port.in.NoteUseCases;
import com.refinements.visualizer.shared.XYPosition;
import com.refinements.visualizer.shared.errors.BadRequestException;
import com.refinements.visualizer.shared.errors.NotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The note module on its own (plus the open shared module), through its use cases: the template for module tests
 * (specs/005-modular-backend T034).
 */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES, extraIncludes = "shared")
class NoteModuleTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    NoteUseCases notes;

    @Test
    void createsUpdatesListsAndDeletesNotes() {
        NoteView created = notes.create("Ask billing about retries", null, new XYPosition(10.0, 20.0));
        assertThat(created.kind()).isEqualTo(NoteKind.NOTE);

        NoteView updated = notes.update(
                created.id(), new NoteUseCases.NoteChanges(null, NoteKind.QUESTION, new XYPosition(30.0, 40.0)));
        assertThat(updated.text()).isEqualTo("Ask billing about retries");
        assertThat(updated.kind()).isEqualTo(NoteKind.QUESTION);
        assertThat(updated.position()).isEqualTo(new XYPosition(30.0, 40.0));

        assertThat(notes.list()).extracting(NoteView::id).contains(created.id());

        notes.delete(created.id());
        assertThat(notes.list()).extracting(NoteView::id).doesNotContain(created.id());
    }

    @Test
    void rejectsBlankTextAndUnknownNotes() {
        assertThatThrownBy(() -> notes.create("  ", null, new XYPosition(0.0, 0.0)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> notes.delete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }
}
