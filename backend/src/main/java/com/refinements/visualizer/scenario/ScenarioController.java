package com.refinements.visualizer.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.refinements.visualizer.scenario.dto.ScenarioDocument;
import com.refinements.visualizer.scenario.dto.ScenarioDto;
import com.refinements.visualizer.scenario.dto.ScenarioSummary;
import com.refinements.visualizer.scenario.dto.ScenarioWrite;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    private final ScenarioService service;

    public ScenarioController(ScenarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<ScenarioSummary> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<ScenarioDto> create(@Valid @RequestBody ScenarioWrite write) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(write));
    }

    @PostMapping("/import")
    public ResponseEntity<ScenarioDto> importDocument(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content = @Content(schema = @Schema(implementation = ScenarioDocument.class)))
                    @RequestBody
                    JsonNode document) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.importDocument(document));
    }

    @GetMapping("/{scenarioId}")
    public ScenarioDto get(@PathVariable UUID scenarioId) {
        return service.get(scenarioId);
    }

    @PutMapping("/{scenarioId}")
    public ScenarioDto update(@PathVariable UUID scenarioId, @Valid @RequestBody ScenarioWrite write) {
        return service.update(scenarioId, write);
    }

    @DeleteMapping("/{scenarioId}")
    public ResponseEntity<Void> delete(@PathVariable UUID scenarioId) {
        service.delete(scenarioId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{scenarioId}/export")
    public ResponseEntity<ScenarioDocument> export(@PathVariable UUID scenarioId) {
        ScenarioDocument doc = service.export(scenarioId);
        String filename = doc.name().replaceAll("[^A-Za-z0-9 ._()-]", "_") + ".scenario.json";
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename)
                                .build()
                                .toString())
                .body(doc);
    }
}
