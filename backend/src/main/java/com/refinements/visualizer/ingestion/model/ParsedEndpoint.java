package com.refinements.visualizer.ingestion.model;

import java.util.List;

/** data-model.md ParsedEndpoint, embedded as JSON in ServiceSpec.endpoints. */
public record ParsedEndpoint(
        String endpointKey,
        HttpMethod method,
        String path,
        String operationId,
        String summary,
        List<Field> inputs,
        List<Field> outputs) {

    public ParsedEndpoint {
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
    }
}
