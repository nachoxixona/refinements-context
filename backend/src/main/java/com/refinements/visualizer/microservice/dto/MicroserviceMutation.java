package com.refinements.visualizer.microservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(name = "MicroserviceMutation")
public record MicroserviceMutation(MicroserviceDto microservice, List<UUID> reattachedConnectionIds, long revision) {}
