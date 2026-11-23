package com.refinements.visualizer.connection;

import com.refinements.visualizer.connection.dto.FlowMutation;
import com.refinements.visualizer.connection.dto.FlowPatch;
import com.refinements.visualizer.connection.dto.FlowWrite;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flows")
public class FlowController {

    private final FlowService service;

    public FlowController(FlowService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<FlowMutation> create(@Valid @RequestBody FlowWrite write) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(write.name(), write.color()));
    }

    @PatchMapping("/{flowId}")
    public FlowMutation update(@PathVariable UUID flowId, @Valid @RequestBody FlowPatch patch) {
        return service.update(flowId, patch);
    }

    /** Reorders a flow (spec 004 FR-004). */
    @PutMapping("/{flowId}/steps")
    public java.util.Map<String, Long> steps(
            @PathVariable UUID flowId, @Valid @RequestBody com.refinements.visualizer.connection.dto.StepsWrite write) {
        return java.util.Map.of("revision", service.setSteps(flowId, write.steps()));
    }

    @DeleteMapping("/{flowId}")
    public ResponseEntity<Void> delete(@PathVariable UUID flowId) {
        service.delete(flowId);
        return ResponseEntity.noContent().build();
    }
}
