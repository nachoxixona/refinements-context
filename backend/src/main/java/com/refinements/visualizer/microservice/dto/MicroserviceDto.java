package com.refinements.visualizer.microservice.dto;

import com.refinements.visualizer.microservice.Microservice;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "Microservice")
public record MicroserviceDto(
        UUID id, String name, String description, UUID specCardId, String team, String repositoryUrl, String docsUrl) {

    public static MicroserviceDto from(Microservice m) {
        return new MicroserviceDto(
                m.getId(),
                m.getName(),
                m.getDescription(),
                m.getSpecCardId(),
                m.getTeam(),
                m.getRepositoryUrl(),
                m.getDocsUrl());
    }
}
