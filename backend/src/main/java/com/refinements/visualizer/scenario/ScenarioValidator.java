package com.refinements.visualizer.scenario;

import com.refinements.visualizer.canvas.BaselineIndex;
import com.refinements.visualizer.common.errors.BadRequestException;
import com.refinements.visualizer.connection.ConnectionEndpoints;
import com.refinements.visualizer.connection.ConnectionKey;
import com.refinements.visualizer.connection.Names;
import com.refinements.visualizer.ingestion.SpecKeys;
import com.refinements.visualizer.scenario.dto.Orphan;
import com.refinements.visualizer.scenario.model.AddedEndpoint;
import com.refinements.visualizer.scenario.model.AddedField;
import com.refinements.visualizer.scenario.model.BaselineRef;
import com.refinements.visualizer.scenario.model.ConnectionChange;
import com.refinements.visualizer.scenario.model.ProposedConnection;
import com.refinements.visualizer.scenario.model.ProposedEndpoint;
import com.refinements.visualizer.scenario.model.ProposedLink;
import com.refinements.visualizer.scenario.model.ProposedMicroservice;
import com.refinements.visualizer.scenario.model.ScenarioContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The data-model.md §7 rules for SavedScenario.content: structure (400 on the first problem), orphans
 * (FR-042) and card drift. References to baseline entities that no longer exist are orphans, never errors.
 */
@Component
public class ScenarioValidator {

    public static final String PROPOSED_PREFIX = "proposed:";

    public void validate(ScenarioContent content, BaselineIndex baseline) {
        Map<String, String> proposedNames = new HashMap<>(); // tempKey → normalized name
        Set<String> names = new HashSet<>(baseline.microservices().values());
        for (ProposedMicroservice m : content.proposedMicroservices()) {
            if (!m.tempKey().startsWith(PROPOSED_PREFIX) || m.tempKey().length() == PROPOSED_PREFIX.length()) {
                throw bad("Proposed microservice key '" + m.tempKey() + "' must start with '" + PROPOSED_PREFIX + "'.");
            }
            if (proposedNames.containsKey(m.tempKey())) {
                throw bad("Duplicate proposed microservice key '" + m.tempKey() + "'.");
            }
            String norm = Names.norm(m.name());
            if (norm.isEmpty() || !names.add(norm)) {
                throw bad("A microservice named '" + Names.clean(m.name()) + "' already exists.");
            }
            proposedNames.put(m.tempKey(), norm);
        }

        Set<UUID> removedLinks = new HashSet<>(content.removals().links());
        content.removals().microservices().forEach(removedLinks::add);
        Map<UUID, UUID> cardOwner = new HashMap<>();
        baseline.linkedCards().forEach((ms, card) -> cardOwner.put(card, ms));
        Set<String> linkedMicroservices = new HashSet<>();
        for (ProposedMicroservice m : content.proposedMicroservices()) {
            if (m.card() != null) {
                linkedMicroservices.add(m.tempKey());
            }
        }
        Set<UUID> linkedCards = new HashSet<>();
        for (ProposedLink l : content.proposedLinks()) {
            UUID msId = uuid(l.microservice());
            if (msId == null && !proposedNames.containsKey(l.microservice())) {
                throw bad("Proposed link refers to an unknown microservice '" + l.microservice() + "'.");
            }
            boolean hasCard = msId != null && baseline.linkedCards().containsKey(msId) && !removedLinks.contains(msId);
            if (hasCard || !linkedMicroservices.add(l.microservice())) {
                throw bad("A microservice can have only one spec; propose removing its current link first.");
            }
            UUID owner = cardOwner.get(l.cardId());
            if ((owner != null && !removedLinks.contains(owner)) || !linkedCards.add(l.cardId())) {
                throw bad("That spec is already linked; propose removing its link first.");
            }
        }

        Set<ConnectionKey> keys = new HashSet<>();
        Set<UUID> removedConnections = new HashSet<>(content.removals().connections());
        Map<UUID, ConnectionChange> changes = new HashMap<>();
        for (ConnectionChange c : content.connectionChanges()) {
            if (changes.put(c.connectionId(), c) != null) {
                throw bad("Duplicate change for connection " + c.connectionId() + ".");
            }
        }
        baseline.connections().forEach((id, ref) -> {
            if (!removedConnections.contains(id)) {
                ConnectionChange c = changes.get(id);
                String name = c != null && c.name() != null ? Names.norm(c.name()) : ref.name();
                if (!keys.add(new ConnectionKey(ref.source(), ref.target(), name))) {
                    throw bad(
                            "A proposed connection change would duplicate another connection's name between the same services.");
                }
            }
        });
        Set<String> noteKeys = new HashSet<>();
        for (com.refinements.visualizer.scenario.model.ProposedNote n : content.notes()) {
            if (!n.tempKey().startsWith("proposed-note:") || !noteKeys.add(n.tempKey())) {
                throw bad("Draft notes need unique tempKeys starting with 'proposed-note:'.");
            }
        }
        for (ProposedConnection f : content.proposedConnections()) {
            if (f.step() != null && f.flowId() == null) {
                throw bad("Proposed connection '" + f.name() + "': a step needs a flow.");
            }
        }
        Set<String> connectionIds = new HashSet<>();
        for (ProposedConnection f : content.proposedConnections()) {
            if (!connectionIds.add(f.id())) {
                throw bad("Duplicate proposed connection id '" + f.id() + "'.");
            }
            if (f.source().equals(f.target())) {
                throw bad("Proposed connection '" + f.name() + "' goes from a microservice to itself.");
            }
            String source = endName(f.source(), proposedNames, baseline);
            String target = endName(f.target(), proposedNames, baseline);
            if (source != null && source.equals(target)) {
                throw bad("Proposed connection '" + f.name() + "' goes from a microservice to itself.");
            }
            if (source != null
                    && target != null
                    && !keys.add(new ConnectionKey(source, target, Names.norm(f.name())))) {
                throw bad("A connection named '" + Names.clean(f.name()) + "' already exists between those services.");
            }
        }

        for (ConnectionChange c : content.connectionChanges()) {
            if (c.flowId() != null && !ConnectionChange.NO_FLOW.equals(c.flowId()) && uuid(c.flowId()) == null) {
                throw bad("connectionChanges flowId must be a flow id or NONE.");
            }
            if (c.endpoint() != null
                    && !ConnectionChange.NO_ENDPOINT.equals(c.endpoint())
                    && ConnectionEndpoints.normalize(c.endpoint()) == null) {
                throw bad("connectionChanges endpoint must look like 'POST /invoices', or be NONE.");
            }
        }
        for (ProposedConnection f : content.proposedConnections()) {
            if (f.endpoint() != null
                    && !f.endpoint().isBlank()
                    && ConnectionEndpoints.normalize(f.endpoint()) == null) {
                throw bad("Proposed connection '" + f.name() + "': endpoint must look like 'POST /invoices'.");
            }
        }

        for (AddedField f : content.addedFields()) {
            if (!f.endpointKey().startsWith(f.cardId() + ":")) {
                throw bad("Added field '" + f.field().name() + "': endpoint '" + f.endpointKey()
                        + "' does not belong to card " + f.cardId() + ".");
            }
        }
    }

    /** Normalized name of a connection end; null for a missing baseline id (an orphan, reported separately). */
    private static String endName(String end, Map<String, String> proposed, BaselineIndex baseline) {
        if (end.startsWith(PROPOSED_PREFIX)) {
            String name = proposed.get(end);
            if (name == null) {
                throw bad("Proposed connection refers to an unknown proposed microservice '" + end + "'.");
            }
            return name;
        }
        UUID id = uuid(end);
        if (id == null) {
            return null;
        }
        return baseline.microservices().get(id);
    }

    /** Baseline cards the scenario depends on, with their current hash (for drift detection). */
    public BaselineRef baselineRef(ScenarioContent content, BaselineIndex baseline) {
        Map<UUID, String> cards = new TreeMap<>();
        List<UUID> ids = new ArrayList<>();
        content.addedEndpoints().forEach(a -> ids.add(a.cardId()));
        content.addedFields().forEach(f -> ids.add(f.cardId()));
        content.proposedLinks().forEach(l -> ids.add(l.cardId()));
        for (UUID id : ids) {
            String hash = baseline.cardHashes().get(id);
            if (hash != null) {
                cards.put(id, hash);
            }
        }
        return new BaselineRef(cards, baseline.revision());
    }

    /** Every missing baseline reference, with what uses it (FR-042). */
    public List<Orphan> orphans(ScenarioContent content, BaselineIndex baseline) {
        Set<String> proposedEndpointKeys = new HashSet<>();
        for (AddedEndpoint a : content.addedEndpoints()) {
            ProposedEndpoint ep = a.endpoint();
            proposedEndpointKeys.add(
                    SpecKeys.endpointKey(a.cardId().toString(), ep.method().name(), ep.path()));
        }
        Map<String, List<String>> missing = new LinkedHashMap<>();
        for (int i = 0; i < content.proposedLinks().size(); i++) {
            ProposedLink l = content.proposedLinks().get(i);
            checkMicroservice(missing, l.microservice(), baseline, "proposedLinks[" + i + "]");
            checkCard(missing, l.cardId(), baseline, "proposedLinks[" + i + "]");
        }
        for (int i = 0; i < content.proposedConnections().size(); i++) {
            ProposedConnection f = content.proposedConnections().get(i);
            checkMicroservice(missing, f.source(), baseline, "proposedConnections[" + i + "]");
            checkMicroservice(missing, f.target(), baseline, "proposedConnections[" + i + "]");
        }
        for (int i = 0; i < content.proposedConnections().size(); i++) {
            UUID g = content.proposedConnections().get(i).flowId();
            if (g != null && !baseline.flows().contains(g)) {
                addUse(missing, g.toString(), "proposedConnections[" + i + "]");
            }
        }
        for (int i = 0; i < content.connectionChanges().size(); i++) {
            UUID g = uuid(String.valueOf(content.connectionChanges().get(i).flowId()));
            if (g != null && !baseline.flows().contains(g)) {
                addUse(missing, g.toString(), "connectionChanges[" + i + "]");
            }
        }
        for (int i = 0; i < content.addedEndpoints().size(); i++) {
            checkCard(missing, content.addedEndpoints().get(i).cardId(), baseline, "addedEndpoints[" + i + "]");
        }
        for (int i = 0; i < content.addedFields().size(); i++) {
            AddedField f = content.addedFields().get(i);
            if (!baseline.endpointKeys().contains(f.endpointKey()) && !proposedEndpointKeys.contains(f.endpointKey())) {
                addUse(missing, f.endpointKey(), "addedFields[" + i + "]");
            }
        }
        List<UUID> rf = content.removals().connections();
        for (int i = 0; i < rf.size(); i++) {
            if (!baseline.connections().containsKey(rf.get(i))) {
                addUse(missing, rf.get(i).toString(), "removals.connections[" + i + "]");
            }
        }
        List<UUID> rm = content.removals().microservices();
        for (int i = 0; i < rm.size(); i++) {
            if (!baseline.microservices().containsKey(rm.get(i))) {
                addUse(missing, rm.get(i).toString(), "removals.microservices[" + i + "]");
            }
        }
        List<UUID> rl = content.removals().links();
        for (int i = 0; i < rl.size(); i++) {
            if (!baseline.microservices().containsKey(rl.get(i))) {
                addUse(missing, rl.get(i).toString(), "removals.links[" + i + "]");
            }
        }
        for (int i = 0; i < content.connectionChanges().size(); i++) {
            UUID id = content.connectionChanges().get(i).connectionId();
            if (!baseline.connections().containsKey(id)) {
                addUse(missing, id.toString(), "connectionChanges[" + i + "]");
            }
        }
        List<Orphan> out = new ArrayList<>();
        missing.forEach((ref, usedBy) -> out.add(new Orphan(ref, List.copyOf(usedBy))));
        return out;
    }

    /** Cards whose spec changed since the scenario captured them. */
    public List<UUID> drift(BaselineRef ref, BaselineIndex baseline) {
        List<UUID> out = new ArrayList<>();
        new TreeMap<>(ref.cards()).forEach((cardId, hash) -> {
            String current = baseline.cardHashes().get(cardId);
            if (current != null && !current.equals(hash)) {
                out.add(cardId);
            }
        });
        return out;
    }

    private static void checkMicroservice(
            Map<String, List<String>> missing, String ref, BaselineIndex baseline, String user) {
        if (ref.startsWith(PROPOSED_PREFIX)) {
            return;
        }
        UUID id = uuid(ref);
        if (id == null || !baseline.microservices().containsKey(id)) {
            addUse(missing, ref, user);
        }
    }

    private static void checkCard(Map<String, List<String>> missing, UUID card, BaselineIndex baseline, String user) {
        if (!baseline.cardHashes().containsKey(card)) {
            addUse(missing, card.toString(), user);
        }
    }

    private static void addUse(Map<String, List<String>> missing, String ref, String user) {
        List<String> users = missing.computeIfAbsent(ref, k -> new ArrayList<>());
        if (!users.contains(user)) {
            users.add(user);
        }
    }

    static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BadRequestException bad(String message) {
        return new BadRequestException(message);
    }
}
