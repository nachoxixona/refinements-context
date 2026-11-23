package com.refinements.visualizer.connection.dto;

import com.refinements.visualizer.connection.ConnectionDto;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ConnectionMutation")
public record ConnectionMutation(ConnectionDto connection, long revision) {}
