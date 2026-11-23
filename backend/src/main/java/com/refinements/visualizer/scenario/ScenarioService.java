package com.refinements.visualizer.scenario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.refinements.visualizer.canvas.BaselineIndex;
import com.refinements.visualizer.canvas.CanvasService;
import com.refinements.visualizer.common.errors.BadRequestException;
import com.refinements.visualizer.common.errors.ConflictException;
import com.refinements.visualizer.common.errors.NotFoundException;
import com.refinements.visualizer.common.errors.StaleVersionException;
import com.refinements.visualizer.scenario.dto.ScenarioDocument;
import com.refinements.visualizer.scenario.dto.ScenarioDto;
import com.refinements.visualizer.scenario.dto.ScenarioSummary;
import com.refinements.visualizer.scenario.dto.ScenarioWrite;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saved scenarios (FR-036 – FR-043). Reads the baseline only: never writes microservices, cards, connections or the
 * layout, and never bumps the workspace revision (Principle IV, FR-039).
 */
@Service
public class ScenarioService {

    private static final int MAX_NAME = 120;

    private final SavedScenarioRepository repository;
    private final ScenarioValidator validator;
    private final CanvasService canvas;
    private final ObjectMapper mapper;
    private final Validator beanValidator;
    private final Clock clock;

    public ScenarioService(
            SavedScenarioRepository repository,
            ScenarioValidator validator,
            CanvasService canvas,
            ObjectMapper mapper,
            Validator beanValidator,
            Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.canvas = canvas;
        this.mapper = mapper;
        this.beanValidator = beanValidator;
        this.clock = clock;
    }

    public List<ScenarioSummary> list() {
        return repository.findAllByOrderByUpdatedAtDesc().stream()
                .map(ScenarioSummary::from)
                .toList();
    }

    public ScenarioDto get(UUID id) {
        return toDto(find(id), canvas.index());
    }

    @Transactional
    public ScenarioDto create(ScenarioWrite write) {
        BaselineIndex baseline = canvas.index();
        validator.validate(write.content(), baseline);
        String norm = SavedScenario.normalizeName(write.name());
        if (repository.existsByNameNormalized(norm)) {
            throw duplicateName(write.name());
        }
        SavedScenario s = new SavedScenario(clock.instant());
        s.update(
                write.name(),
                write.description(),
                write.content(),
                validator.baselineRef(write.content(), baseline),
                clock.instant());
        return toDto(repository.saveAndFlush(s), baseline);
    }

    @Transactional
    public ScenarioDto update(UUID id, ScenarioWrite write) {
        if (write.version() == null) {
            throw new BadRequestException("version is required when updating a scenario.");
        }
        SavedScenario s = find(id);
        if (s.getVersion() != write.version()) {
            throw new StaleVersionException("The scenario changed since it was loaded (for example in another tab).");
        }
        BaselineIndex baseline = canvas.index();
        validator.validate(write.content(), baseline);
        if (repository.existsByNameNormalizedAndIdNot(SavedScenario.normalizeName(write.name()), id)) {
            throw duplicateName(write.name());
        }
        s.update(
                write.name(),
                write.description(),
                write.content(),
                validator.baselineRef(write.content(), baseline),
                clock.instant());
        return toDto(repository.saveAndFlush(s), baseline);
    }

    @Transactional
    public void delete(UUID id) {
        repository.delete(find(id));
    }

    /** Imports a scenario document (formatVersion 3), as written by {@link #export}. */
    @Transactional
    public ScenarioDto importDocument(JsonNode body) {
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
        checkBean(doc);
        BaselineIndex baseline = canvas.index();
        validator.validate(doc.content(), baseline);
        SavedScenario s = new SavedScenario(clock.instant());
        s.update(
                freeName(doc.name().trim()),
                doc.description(),
                doc.content(),
                doc.baselineRef() == null ? validator.baselineRef(doc.content(), baseline) : doc.baselineRef(),
                clock.instant());
        return toDto(repository.saveAndFlush(s), baseline);
    }

    public ScenarioDocument export(UUID id) {
        SavedScenario s = find(id);
        return new ScenarioDocument(
                ScenarioDocument.FORMAT_VERSION,
                s.getName(),
                s.getDescription(),
                clock.instant(),
                s.getBaselineRef(),
                s.getContent());
    }

    private void checkBean(Object o) {
        Set<ConstraintViolation<Object>> v = beanValidator.validate(o);
        if (!v.isEmpty()) {
            throw new BadRequestException(v.stream()
                    .map(c -> c.getPropertyPath() + ": " + c.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; ")));
        }
    }

    /** "Checkout v2" → "Checkout v2 (imported)" → "Checkout v2 (imported 2)" … on a name clash. */
    private String freeName(String name) {
        if (!repository.existsByNameNormalized(SavedScenario.normalizeName(name))) {
            return truncate(name, MAX_NAME);
        }
        for (int i = 1; ; i++) {
            String suffix = i == 1 ? " (imported)" : " (imported " + i + ")";
            String candidate = truncate(name, MAX_NAME - suffix.length()) + suffix;
            if (!repository.existsByNameNormalized(SavedScenario.normalizeName(candidate))) {
                return candidate;
            }
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static ConflictException duplicateName(String name) {
        return new ConflictException(
                "A scenario named '" + name.trim() + "' already exists.", ConflictException.DUPLICATE_SCENARIO_NAME);
    }

    private SavedScenario find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("No scenario " + id + "."));
    }

    private ScenarioDto toDto(SavedScenario s, BaselineIndex baseline) {
        return ScenarioDto.from(
                s, validator.orphans(s.getContent(), baseline), validator.drift(s.getBaselineRef(), baseline));
    }
}
