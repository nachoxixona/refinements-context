package com.refinements.visualizer.note;

import java.util.List;

/** What other modules may ask the note module (contracts/module-apis.md §note). */
public interface NoteApi {

    /** All sticky notes, oldest first. */
    List<NoteView> list();
}
