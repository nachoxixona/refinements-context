package com.refinements.visualizer.microservice.adapter.in.web;

import com.refinements.visualizer.microservice.application.port.in.MicroserviceUseCases;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "MicroserviceMutation")
public record MicroserviceMutation(MicroserviceDto microservice, List<UUID> reattachedConnectionIds, long revision) {

    static MicroserviceMutation from(MicroserviceUseCases.Mutation m) {
        return new MicroserviceMutation(
                MicroserviceDto.from(m.microservice()), m.reattachedConnectionIds(), m.revision());
    }
}
