package com.refinements.visualizer.scenario.model;

import com.refinements.visualizer.shared.XYPosition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

/**
 * Scenario content v2 (research R9, data-model.md §7 SavedScenario.content). Proposals reference baseline
 * entities by id and never mutate them (Principle IV). Every list defaults to empty.
 */
@Schema(name = "ScenarioContent")
public record ScenarioContent(
        List<@Valid ProposedMicroservice> proposedMicroservices,
        List<@Valid ProposedLink> proposedLinks,
        List<@Valid ProposedConnection> proposedConnections,
        List<@Valid AddedEndpoint> addedEndpoints,
        List<@Valid AddedField> addedFields,
        @Valid ScenarioRemovals removals,
        List<@Valid ConnectionChange> connectionChanges,
        Map<String, @Valid @NotNull XYPosition> positions,
        List<@Valid ProposedNote> notes) {

    public ScenarioContent(
            List<ProposedMicroservice> proposedMicroservices,
            List<ProposedLink> proposedLinks,
            List<ProposedConnection> proposedConnections,
            List<AddedEndpoint> addedEndpoints,
            List<AddedField> addedFields,
            ScenarioRemovals removals,
            List<ConnectionChange> connectionChanges,
            Map<String, XYPosition> positions) {
        this(
                proposedMicroservices,
                proposedLinks,
                proposedConnections,
                addedEndpoints,
                addedFields,
                removals,
                connectionChanges,
                positions,
                null);
    }

    public ScenarioContent {
        proposedMicroservices = copy(proposedMicroservices);
        proposedLinks = copy(proposedLinks);
        proposedConnections = copy(proposedConnections);
        addedEndpoints = copy(addedEndpoints);
        addedFields = copy(addedFields);
        removals = removals == null ? ScenarioRemovals.EMPTY : removals;
        connectionChanges = copy(connectionChanges);
        positions = positions == null ? Map.of() : Map.copyOf(positions);
        notes = copy(notes);
    }

    public static ScenarioContent empty() {
        return new ScenarioContent(null, null, null, null, null, null, null, null, null);
    }

    private static <T> List<T> copy(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}
