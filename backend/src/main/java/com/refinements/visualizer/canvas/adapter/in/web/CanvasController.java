package com.refinements.visualizer.canvas.adapter.in.web;

import com.refinements.visualizer.canvas.application.port.in.CanvasUseCases;
import com.refinements.visualizer.canvas.domain.CanvasResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/canvas")
public class CanvasController {

    private final CanvasUseCases canvas;

    public CanvasController(CanvasUseCases canvas) {
        this.canvas = canvas;
    }

    @GetMapping
    public CanvasResponse canvas() {
        return canvas.canvas();
    }
}
