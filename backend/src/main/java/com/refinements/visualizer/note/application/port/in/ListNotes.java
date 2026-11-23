package com.refinements.visualizer.note.application.port.in;

import com.refinements.visualizer.note.NoteDto;
import java.util.List;

public interface ListNotes {

    /** All notes, oldest first. */
    List<NoteDto> list();
}
