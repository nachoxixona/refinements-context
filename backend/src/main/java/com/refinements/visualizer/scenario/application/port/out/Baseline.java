package com.refinements.visualizer.scenario.application.port.out;

import com.refinements.visualizer.canvas.BaselineIndex;

/** What exists in the baseline right now (the canvas module behind it). Read only. */
public interface Baseline {

    BaselineIndex index();
}
