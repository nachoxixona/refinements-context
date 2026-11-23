package com.refinements.visualizer.layout;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code PUT /api/layout}: replaces positions and collapsed cards with optimistic locking (FR-014, FR-035). */
@RestController
@RequestMapping("/api/layout")
public class LayoutController {

    public record LayoutVersion(int version) {}

    private final LayoutService layouts;

    public LayoutController(LayoutService layouts) {
        this.layouts = layouts;
    }

    @PutMapping
    public LayoutVersion save(@Valid @RequestBody LayoutUpdate update) {
        return new LayoutVersion(layouts.save(update));
    }
}
