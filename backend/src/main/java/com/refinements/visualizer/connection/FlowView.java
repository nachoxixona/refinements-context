package com.refinements.visualizer.connection;

import java.util.UUID;

/** A flow as other modules see it. Same fields as the published REST schema {@code Flow}. */
public record FlowView(UUID id, String name, FlowColor color) {}
