package com.refinements.visualizer.canvas;

import com.refinements.visualizer.canvas.dto.CanvasResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/canvas")
public class CanvasController {

    private final CanvasService service;

    public CanvasController(CanvasService service) {
        this.service = service;
    }

    @GetMapping
    public CanvasResponse canvas() {
        return service.canvas();
    }
}
