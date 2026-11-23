package com.refinements.visualizer.scenario.adapter.out.canvas;

import com.refinements.visualizer.canvas.BaselineIndex;
import com.refinements.visualizer.canvas.CanvasApi;
import com.refinements.visualizer.scenario.application.port.out.Baseline;
import org.springframework.stereotype.Component;

/** {@link Baseline} through the canvas module's API. */
@Component
class BaselineAdapter implements Baseline {

    private final CanvasApi canvas;

    BaselineAdapter(CanvasApi canvas) {
        this.canvas = canvas;
    }

    @Override
    public BaselineIndex index() {
        return canvas.index();
    }
}
