package com.refinements.visualizer.connection;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(name = "Flow")
public record FlowDto(UUID id, String name, FlowColor color) {

    public static FlowDto from(Flow g) {
        return new FlowDto(g.getId(), g.getName(), g.getColor());
    }
}
