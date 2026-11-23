package com.refinements.visualizer.scenario.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ProposedCard(@NotBlank String title, List<@Valid ProposedEndpoint> endpoints) {

    public ProposedCard {
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
    }
}
