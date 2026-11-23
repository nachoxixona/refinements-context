package com.refinements.visualizer.connection.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ConnectionMutation")
public record ConnectionMutation(ConnectionDto connection, long revision) {}
