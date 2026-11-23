package com.refinements.visualizer.microservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "MicroserviceWrite")
public record MicroserviceWrite(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description,
        @Size(max = 120) String team,
        @Size(max = 2048) String repositoryUrl,
        @Size(max = 2048) String docsUrl) {

    public MicroserviceWrite(String name, String description) {
        this(name, description, null, null, null);
    }
}
