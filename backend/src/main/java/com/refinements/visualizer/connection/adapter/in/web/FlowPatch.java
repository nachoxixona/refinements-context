package com.refinements.visualizer.connection.adapter.in.web;

import com.refinements.visualizer.connection.FlowColor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(name = "FlowPatch")
public record FlowPatch(@Size(min = 1, max = 120) String name, FlowColor color) {}
