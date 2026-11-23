package com.refinements.visualizer.canvas;

/** What other modules may ask the canvas module (contracts/module-apis.md §canvas). */
public interface CanvasApi {

    /** What exists in the baseline right now, for scenario orphan and drift checks. */
    BaselineIndex index();
}
