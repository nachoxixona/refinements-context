package com.refinements.visualizer.microservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.refinements.visualizer.microservice.dto.LinkCardRequest;
import com.refinements.visualizer.microservice.dto.MicroserviceDeleted;
import com.refinements.visualizer.microservice.dto.MicroserviceMutation;
import com.refinements.visualizer.microservice.dto.MicroservicePatch;
import com.refinements.visualizer.microservice.dto.MicroserviceWrite;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/microservices")
public class MicroserviceController {

    private final MicroserviceService service;

    public MicroserviceController(MicroserviceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<MicroserviceMutation> create(@Valid @RequestBody MicroserviceWrite write) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(
                        write.name(), write.description(), write.team(), write.repositoryUrl(), write.docsUrl()));
    }

    @PatchMapping("/{microserviceId}")
    public MicroserviceMutation update(
            @PathVariable UUID microserviceId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content = @Content(schema = @Schema(implementation = MicroservicePatch.class)))
                    @RequestBody
                    JsonNode body) {
        return service.update(microserviceId, MicroservicePatch.from(body));
    }

    @DeleteMapping("/{microserviceId}")
    public MicroserviceDeleted delete(@PathVariable UUID microserviceId) {
        return service.delete(microserviceId);
    }

    @PutMapping("/{microserviceId}/card")
    public ResponseEntity<Void> link(@PathVariable UUID microserviceId, @Valid @RequestBody LinkCardRequest request) {
        service.link(microserviceId, request.cardId(), request.replaceOrFalse());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{microserviceId}/card")
    public ResponseEntity<Void> unlink(@PathVariable UUID microserviceId) {
        service.unlink(microserviceId);
        return ResponseEntity.noContent().build();
    }
}
