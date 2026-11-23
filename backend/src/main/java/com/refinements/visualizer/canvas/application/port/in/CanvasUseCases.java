package com.refinements.visualizer.canvas.application.port.in;

import com.refinements.visualizer.canvas.domain.CanvasResponse;

/** What the canvas module offers its inbound adapters. */
public interface CanvasUseCases {

    /** The baseline canvas: nodes, edges, dangling connections, flows, notes, revision and layout version. */
    CanvasResponse canvas();
}
