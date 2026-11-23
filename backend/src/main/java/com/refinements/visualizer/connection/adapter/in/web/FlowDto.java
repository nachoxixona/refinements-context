package com.refinements.visualizer.connection.adapter.in.web;

import com.refinements.visualizer.connection.FlowColor;
import com.refinements.visualizer.connection.FlowView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "Flow")
public record FlowDto(UUID id, String name, FlowColor color) {

    public static FlowDto from(FlowView v) {
        return new FlowDto(v.id(), v.name(), v.color());
    }
}
