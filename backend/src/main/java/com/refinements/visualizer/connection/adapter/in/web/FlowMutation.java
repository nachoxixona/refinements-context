package com.refinements.visualizer.connection.adapter.in.web;

import com.refinements.visualizer.connection.application.port.in.FlowUseCases.Mutation;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FlowMutation")
public record FlowMutation(FlowDto flow, long revision) {

    static FlowMutation from(Mutation m) {
        return new FlowMutation(FlowDto.from(m.flow()), m.revision());
    }
}
