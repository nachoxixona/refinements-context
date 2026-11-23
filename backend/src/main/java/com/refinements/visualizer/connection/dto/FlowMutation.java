package com.refinements.visualizer.connection.dto;

import com.refinements.visualizer.connection.FlowDto;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FlowMutation")
public record FlowMutation(FlowDto flow, long revision) {}
