package com.refinements.visualizer.scenario.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refinements.visualizer.scenario.application.port.in.ScenarioUseCases;
import com.refinements.visualizer.scenario.application.port.in.ScenarioUseCases.ExportedScenario;
import com.refinements.visualizer.scenario.application.port.in.ScenarioUseCases.ImportedScenario;
import com.refinements.visualizer.shared.errors.BadRequestException;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

    private final ScenarioUseCases scenarios;
    private final ObjectMapper mapper;
    private final Validator beanValidator;

    public ScenarioController(ScenarioUseCases scenarios, ObjectMapper mapper, Validator beanValidator) {
        this.scenarios = scenarios;
        this.mapper = mapper;
        this.beanValidator = beanValidator;
    }

    @GetMapping
    public List<ScenarioSummary> list() {
        return scenarios.list().stream().map(ScenarioSummary::from).toList();
    }

    @PostMapping
    public ResponseEntity<ScenarioDto> create(@Valid @RequestBody ScenarioWrite write) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ScenarioDto.from(scenarios.create(command(write))));
    }

    /** Imports a scenario document (formatVersion 3), as written by {@link #export}. */
    @PostMapping("/import")
    public ResponseEntity<ScenarioDto> importDocument(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content = @Content(schema = @Schema(implementation = ScenarioDocument.class)))
                    @RequestBody
                    JsonNode body) {
        ScenarioDocument doc = parse(body);
        ImportedScenario imported =
                new ImportedScenario(doc.name(), doc.description(), doc.baselineRef(), doc.content());
        return ResponseEntity.status(HttpStatus.CREATED).body(ScenarioDto.from(scenarios.importScenario(imported)));
    }

    @GetMapping("/{scenarioId}")
    public ScenarioDto get(@PathVariable UUID scenarioId) {
        return ScenarioDto.from(scenarios.get(scenarioId));
    }

    @PutMapping("/{scenarioId}")
    public ScenarioDto update(@PathVariable UUID scenarioId, @Valid @RequestBody ScenarioWrite write) {
        return ScenarioDto.from(scenarios.update(scenarioId, command(write)));
    }

    @DeleteMapping("/{scenarioId}")
    public ResponseEntity<Void> delete(@PathVariable UUID scenarioId) {
        scenarios.delete(scenarioId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{scenarioId}/export")
    public ResponseEntity<ScenarioDocument> export(@PathVariable UUID scenarioId) {
        ExportedScenario e = scenarios.export(scenarioId);
        ScenarioDocument doc = new ScenarioDocument(
                ScenarioDocument.FORMAT_VERSION,
                e.name(),
                e.description(),
                e.exportedAt(),
                e.baselineRef(),
                e.content());
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

    private static ScenarioUseCases.ScenarioWrite command(ScenarioWrite w) {
        return new ScenarioUseCases.ScenarioWrite(w.name(), w.description(), w.content(), w.version());
    }

    /** formatVersion, name, shape and Bean Validation of an imported document, in that order. */
    private ScenarioDocument parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new BadRequestException("The scenario document must be a JSON object.");
        }
        int version = body.path("formatVersion").asInt(-1);
        if (version != ScenarioDocument.FORMAT_VERSION) {
            throw new BadRequestException("Unsupported scenario formatVersion " + body.path("formatVersion")
                    + "; expected " + ScenarioDocument.FORMAT_VERSION + ".");
        }
        if (body.path("name").asText("").isBlank()) {
            throw new BadRequestException("name is required.");
        }
        ScenarioDocument doc;
        try {
            doc = mapper.treeToValue(body, ScenarioDocument.class);
        } catch (Exception e) {
            throw new BadRequestException("The scenario document is not valid: " + e.getMessage());
        }
        Set<ConstraintViolation<Object>> v = beanValidator.validate(doc);
        if (!v.isEmpty()) {
            throw new BadRequestException(v.stream()
                    .map(c -> c.getPropertyPath() + ": " + c.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; ")));
        }
        return doc;
    }
}
