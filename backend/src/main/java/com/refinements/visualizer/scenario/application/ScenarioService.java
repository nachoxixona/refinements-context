package com.refinements.visualizer.scenario.application;

import com.refinements.visualizer.canvas.BaselineIndex;
import com.refinements.visualizer.scenario.application.port.in.ScenarioUseCases;
import com.refinements.visualizer.scenario.application.port.out.Baseline;
import com.refinements.visualizer.scenario.application.port.out.ScenarioStore;
import com.refinements.visualizer.scenario.domain.Scenario;
import com.refinements.visualizer.scenario.domain.ScenarioValidator;
import com.refinements.visualizer.shared.errors.BadRequestException;
import com.refinements.visualizer.shared.errors.ConflictException;
import com.refinements.visualizer.shared.errors.NotFoundException;
import com.refinements.visualizer.shared.errors.StaleVersionException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saved scenarios (FR-036 – FR-043). Reads the baseline only: never writes microservices, cards, connections or the
 * layout, and never bumps the workspace revision (Principle IV, FR-039). It has no port to do so.
 */
@Service
class ScenarioService implements ScenarioUseCases {

    private final ScenarioStore store;
    private final Baseline baseline;
    private final Clock clock;
    private final ScenarioValidator validator = new ScenarioValidator();

    ScenarioService(ScenarioStore store, Baseline baseline, Clock clock) {
        this.store = store;
        this.baseline = baseline;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScenarioSummary> list() {
        return store.findAllNewestFirst().stream()
                .map(s -> new ScenarioSummary(
                        s.getId(), s.getName(), s.getDescription(), s.getVersion(), s.getCreatedAt(), s.getUpdatedAt()))
                .toList();
    }

    @Override
    public ScenarioDetails get(UUID id) {
        return details(find(id), baseline.index());
    }

    @Override
    @Transactional
    public ScenarioDetails create(ScenarioWrite write) {
        BaselineIndex index = baseline.index();
        validator.validate(write.content(), index);
        if (store.existsByNameNormalized(Scenario.normalizeName(write.name()))) {
            throw duplicateName(write.name());
        }
        Scenario s = Scenario.create(clock.instant());
        s.update(
                write.name(),
                write.description(),
                write.content(),
                validator.baselineRef(write.content(), index),
                clock.instant());
        return details(store.save(s), index);
    }

    @Override
    @Transactional
    public ScenarioDetails update(UUID id, ScenarioWrite write) {
        if (write.version() == null) {
            throw new BadRequestException("version is required when updating a scenario.");
        }
        Scenario s = find(id);
        if (s.getVersion() != write.version()) {
            throw new StaleVersionException("The scenario changed since it was loaded (for example in another tab).");
        }
        BaselineIndex index = baseline.index();
        validator.validate(write.content(), index);
        if (store.existsByNameNormalizedAndIdNot(Scenario.normalizeName(write.name()), id)) {
            throw duplicateName(write.name());
        }
        s.update(
                write.name(),
                write.description(),
                write.content(),
                validator.baselineRef(write.content(), index),
                clock.instant());
        return details(store.save(s), index);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        store.delete(find(id).getId());
    }

    @Override
    @Transactional
    public ScenarioDetails importScenario(ImportedScenario doc) {
        BaselineIndex index = baseline.index();
        validator.validate(doc.content(), index);
        Scenario s = Scenario.create(clock.instant());
        s.update(
                freeName(doc.name().trim()),
                doc.description(),
                doc.content(),
                doc.baselineRef() == null ? validator.baselineRef(doc.content(), index) : doc.baselineRef(),
                clock.instant());
        return details(store.save(s), index);
    }

    @Override
    @Transactional(readOnly = true)
    public ExportedScenario export(UUID id) {
        Scenario s = find(id);
        return new ExportedScenario(
                s.getName(), s.getDescription(), clock.instant(), s.getBaselineRef(), s.getContent());
    }

    /** "Checkout v2" → "Checkout v2 (imported)" → "Checkout v2 (imported 2)" … on a name clash. */
    private String freeName(String name) {
        if (!store.existsByNameNormalized(Scenario.normalizeName(name))) {
            return truncate(name, Scenario.MAX_NAME);
        }
        for (int i = 1; ; i++) {
            String suffix = i == 1 ? " (imported)" : " (imported " + i + ")";
            String candidate = truncate(name, Scenario.MAX_NAME - suffix.length()) + suffix;
            if (!store.existsByNameNormalized(Scenario.normalizeName(candidate))) {
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

    private Scenario find(UUID id) {
        return store.findById(id).orElseThrow(() -> new NotFoundException("No scenario " + id + "."));
    }

    private ScenarioDetails details(Scenario s, BaselineIndex index) {
        return new ScenarioDetails(
                s.getId(),
                s.getName(),
                s.getDescription(),
                s.getVersion(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                s.getContent(),
                validator.orphans(s.getContent(), index),
                validator.drift(s.getBaselineRef(), index));
    }
}
