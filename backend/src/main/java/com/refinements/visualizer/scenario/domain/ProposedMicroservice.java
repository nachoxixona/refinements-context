package com.refinements.visualizer.scenario.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code tempKey} starts with {@code proposed:}; {@code card} is an optional proposed spec card. */
@Schema(name = "ProposedMicroservice")
public record ProposedMicroservice(
        @NotBlank String tempKey,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @Valid ProposedCard card,
        @Size(max = 120) String team) {

    public ProposedMicroservice(String tempKey, String name, String description, ProposedCard card) {
        this(tempKey, name, description, card, null);
    }
}
