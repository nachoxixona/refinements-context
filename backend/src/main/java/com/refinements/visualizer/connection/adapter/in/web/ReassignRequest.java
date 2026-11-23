package com.refinements.visualizer.connection.adapter.in.web;

import java.util.UUID;

public record ReassignRequest(UUID sourceId, UUID targetId) {}
