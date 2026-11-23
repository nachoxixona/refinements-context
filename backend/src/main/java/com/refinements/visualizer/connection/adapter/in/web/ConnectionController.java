package com.refinements.visualizer.connection.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.refinements.visualizer.connection.application.port.in.ConnectionUseCases;
import com.refinements.visualizer.connection.application.port.in.ConnectionUseCases.ConnectionChanges;
import com.refinements.visualizer.connection.application.port.in.ConnectionUseCases.NewConnection;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/connections")
public class ConnectionController {

    private final ConnectionUseCases connections;

    public ConnectionController(ConnectionUseCases connections) {
        this.connections = connections;
    }

    @PostMapping
    public ResponseEntity<ConnectionMutation> create(@Valid @RequestBody ConnectionCreate request) {
        NewConnection command = new NewConnection(
                request.sourceId(),
                request.targetId(),
                request.name(),
                request.kind(),
                request.description(),
                request.flowId(),
                request.endpoint(),
                request.step());
        return ResponseEntity.status(HttpStatus.CREATED).body(ConnectionMutation.from(connections.create(command)));
    }

    @PatchMapping("/{connectionId}")
    public ConnectionMutation update(
            @PathVariable UUID connectionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content = @Content(schema = @Schema(implementation = ConnectionPatch.class)))
                    @RequestBody
                    JsonNode body) {
        ConnectionPatch p = ConnectionPatch.from(body);
        ConnectionChanges changes = new ConnectionChanges(
                p.name(),
                p.kind(),
                p.kindPresent(),
                p.description(),
                p.descriptionPresent(),
                p.flowId() == null ? null : UUID.fromString(p.flowId()),
                p.flowPresent(),
                p.endpoint(),
                p.endpointPresent(),
                p.step(),
                p.stepPresent());
        return ConnectionMutation.from(connections.update(connectionId, changes));
    }

    @DeleteMapping("/{connectionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID connectionId) {
        connections.delete(connectionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{connectionId}/reassign")
    public ConnectionMutation reassign(@PathVariable UUID connectionId, @RequestBody ReassignRequest request) {
        return ConnectionMutation.from(connections.reassign(connectionId, request.sourceId(), request.targetId()));
    }
}
