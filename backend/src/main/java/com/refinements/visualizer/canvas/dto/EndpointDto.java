package com.refinements.visualizer.canvas.dto;

import com.refinements.visualizer.ingestion.model.HttpMethod;
import com.refinements.visualizer.ingestion.model.ParsedEndpoint;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "Endpoint")
public record EndpointDto(
        String endpointKey,
        HttpMethod method,
        String path,
        String operationId,
        String summary,
        List<FieldDto> inputs,
        List<FieldDto> outputs) {

    public static EndpointDto from(ParsedEndpoint e) {
        return new EndpointDto(
                e.endpointKey(),
                e.method(),
                e.path(),
                e.operationId(),
                e.summary(),
                e.inputs().stream().map(FieldDto::from).toList(),
                e.outputs().stream().map(FieldDto::from).toList());
    }
}
