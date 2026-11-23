package com.refinements.visualizer.connection.dto;

import java.util.UUID;

public record ReassignRequest(UUID sourceId, UUID targetId) {}
