package com.refinements.visualizer.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.refinements.visualizer.connection.dto.ConnectionCreate;
import com.refinements.visualizer.connection.dto.ConnectionMutation;
import com.refinements.visualizer.connection.dto.ConnectionPatch;
import com.refinements.visualizer.connection.dto.ReassignRequest;
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

    private final ConnectionService service;

    public ConnectionController(ConnectionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ConnectionMutation> create(@Valid @RequestBody ConnectionCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PatchMapping("/{connectionId}")
    public ConnectionMutation update(
            @PathVariable UUID connectionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content = @Content(schema = @Schema(implementation = ConnectionPatch.class)))
                    @RequestBody
                    JsonNode body) {
        return service.update(connectionId, ConnectionPatch.from(body));
    }

    @DeleteMapping("/{connectionId}")
    public ResponseEntity<Void> delete(@PathVariable UUID connectionId) {
        service.delete(connectionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{connectionId}/reassign")
    public ConnectionMutation reassign(@PathVariable UUID connectionId, @RequestBody ReassignRequest request) {
        return service.reassign(connectionId, request);
    }
}
