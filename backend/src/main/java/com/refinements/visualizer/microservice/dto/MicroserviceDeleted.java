package com.refinements.visualizer.microservice.dto;

import java.util.List;
import java.util.UUID;

public record MicroserviceDeleted(List<UUID> danglingConnectionIds, long revision) {}
