package com.refinements.visualizer.connection.adapter.in.web;

import com.refinements.visualizer.connection.application.port.in.ConnectionUseCases.Mutation;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ConnectionMutation")
public record ConnectionMutation(ConnectionDto connection, long revision) {

    static ConnectionMutation from(Mutation m) {
        return new ConnectionMutation(ConnectionDto.from(m.connection()), m.revision());
    }
}
