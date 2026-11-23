package com.refinements.visualizer.microservice.adapter.in.web;

import com.refinements.visualizer.microservice.MicroserviceView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "Microservice")
public record MicroserviceDto(
        UUID id, String name, String description, UUID specCardId, String team, String repositoryUrl, String docsUrl) {

    static MicroserviceDto from(MicroserviceView m) {
        return new MicroserviceDto(
                m.id(), m.name(), m.description(), m.specCardId(), m.team(), m.repositoryUrl(), m.docsUrl());
    }
}
