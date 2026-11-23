package com.refinements.visualizer.connection.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FlowMutation")
public record FlowMutation(FlowDto flow, long revision) {}
