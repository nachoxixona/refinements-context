package com.refinements.visualizer.microservice.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.refinements.visualizer.microservice.application.port.in.MicroserviceUseCases;
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

    private final MicroserviceUseCases microservices;

    MicroserviceController(MicroserviceUseCases microservices) {
        this.microservices = microservices;
    }

    @PostMapping
    public ResponseEntity<MicroserviceMutation> create(@Valid @RequestBody MicroserviceWrite write) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(MicroserviceMutation.from(microservices.create(new MicroserviceUseCases.NewMicroservice(
                        write.name(), write.description(), write.team(), write.repositoryUrl(), write.docsUrl()))));
    }

    @PatchMapping("/{microserviceId}")
    public MicroserviceMutation update(
            @PathVariable UUID microserviceId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content = @Content(schema = @Schema(implementation = MicroservicePatch.class)))
                    @RequestBody
                    JsonNode body) {
        return MicroserviceMutation.from(microservices.update(
                microserviceId, MicroservicePatch.from(body).toChanges()));
    }

    @DeleteMapping("/{microserviceId}")
    public MicroserviceDeleted delete(@PathVariable UUID microserviceId) {
        MicroserviceUseCases.Deletion d = microservices.delete(microserviceId);
        return new MicroserviceDeleted(d.danglingConnectionIds(), d.revision());
    }

    @PutMapping("/{microserviceId}/card")
    public ResponseEntity<Void> link(@PathVariable UUID microserviceId, @Valid @RequestBody LinkCardRequest request) {
        microservices.link(microserviceId, request.cardId(), request.replaceOrFalse());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{microserviceId}/card")
    public ResponseEntity<Void> unlink(@PathVariable UUID microserviceId) {
        microservices.unlink(microserviceId);
        return ResponseEntity.noContent().build();
    }
}
