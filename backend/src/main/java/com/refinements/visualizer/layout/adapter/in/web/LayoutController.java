package com.refinements.visualizer.layout.adapter.in.web;

import com.refinements.visualizer.layout.application.port.in.LayoutUseCases;
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

    private final LayoutUseCases layouts;

    LayoutController(LayoutUseCases layouts) {
        this.layouts = layouts;
    }

    @PutMapping
    public LayoutVersion save(@Valid @RequestBody LayoutUpdate update) {
        return new LayoutVersion(layouts.save(
                new LayoutUseCases.SaveLayout(update.version(), update.positions(), update.collapsedCards())));
    }
}
