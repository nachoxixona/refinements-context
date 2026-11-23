package com.refinements.visualizer.scenario.model;

import com.refinements.visualizer.ingestion.model.HttpMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@io.swagger.v3.oas.annotations.media.Schema(name = "ProposedEndpoint")
public record ProposedEndpoint(
        @NotNull HttpMethod method,
        @NotBlank String path,
        List<@Valid SimpleField> inputs,
        List<@Valid SimpleField> outputs) {

    public ProposedEndpoint {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
    }
}
