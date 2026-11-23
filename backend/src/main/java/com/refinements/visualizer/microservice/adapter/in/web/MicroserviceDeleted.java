package com.refinements.visualizer.microservice.adapter.in.web;

import java.util.List;
import java.util.UUID;

public record MicroserviceDeleted(List<UUID> danglingConnectionIds, long revision) {}
