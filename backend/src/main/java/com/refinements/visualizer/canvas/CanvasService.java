package com.refinements.visualizer.canvas;

import com.refinements.visualizer.canvas.dto.CanvasEdge;
import com.refinements.visualizer.canvas.dto.CanvasNode;
import com.refinements.visualizer.canvas.dto.CanvasResponse;
import com.refinements.visualizer.canvas.dto.ConnectionEdgeData;
import com.refinements.visualizer.canvas.dto.EndpointDto;
import com.refinements.visualizer.canvas.dto.MicroserviceNodeData;
import com.refinements.visualizer.canvas.dto.SpecCardNodeData;
import com.refinements.visualizer.card.SpecCardApi;
import com.refinements.visualizer.card.SpecCardView;
import com.refinements.visualizer.card.model.ParsedEndpoint;
import com.refinements.visualizer.connection.ConnectionApi;
import com.refinements.visualizer.connection.ConnectionDto;
import com.refinements.visualizer.connection.ConnectionEndpoints;
import com.refinements.visualizer.connection.ConnectionKey;
import com.refinements.visualizer.connection.ConnectionView;
import com.refinements.visualizer.connection.FlowApi;
import com.refinements.visualizer.connection.FlowDto;
import com.refinements.visualizer.layout.LayoutApi;
import com.refinements.visualizer.layout.LayoutKeys;
import com.refinements.visualizer.layout.LayoutView;
import com.refinements.visualizer.microservice.MicroserviceApi;
import com.refinements.visualizer.microservice.MicroserviceView;
import com.refinements.visualizer.note.NoteApi;
import com.refinements.visualizer.shared.XYPosition;
import com.refinements.visualizer.workspace.WorkspaceRevision;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds {@code GET /api/canvas} (data-model.md §1–§5, research R8) from the other modules' public APIs, inside one
 * read-only transaction so it reads a single consistent snapshot (specs/005 research R6).
 */
@Service
public class CanvasService implements CanvasApi {

    /** Relative position of a docked card: just below its microservice node. */
    public static final XYPosition DOCK_OFFSET = new XYPosition(0.0, 84.0);

    private final MicroserviceApi microservices;
    private final SpecCardApi cards;
    private final ConnectionApi connections;
    private final FlowApi flows;
    private final LayoutApi layouts;
    private final WorkspaceRevision revision;
    private final NoteApi notes;

    public CanvasService(
            MicroserviceApi microservices,
            SpecCardApi cards,
            ConnectionApi connections,
            FlowApi flows,
            LayoutApi layouts,
            WorkspaceRevision revision,
            NoteApi notes) {
        this.microservices = microservices;
        this.cards = cards;
        this.connections = connections;
        this.flows = flows;
        this.layouts = layouts;
        this.revision = revision;
        this.notes = notes;
    }

    @Transactional(readOnly = true)
    public CanvasResponse canvas() {
        LayoutView layout = layouts.current();
        Set<UUID> collapsed = new HashSet<>(layout.collapsedCards());
        Map<UUID, SpecCardView> cardsById = new LinkedHashMap<>();
        cards.findAll().forEach(c -> cardsById.put(c.id(), c)); // oldest first (createdAt, id)

        List<MicroserviceView> allMicroservices = microservices.findAllOrdered();
        List<CanvasNode> nodes = new ArrayList<>();
        Map<UUID, UUID> cardOwner = new HashMap<>();
        for (MicroserviceView m : allMicroservices) {
            SpecCardView card = m.specCardId() == null ? null : cardsById.get(m.specCardId());
            boolean cardCollapsed = card != null && collapsed.contains(card.id());
            nodes.add(new CanvasNode(
                    LayoutKeys.msKey(m.id()),
                    CanvasNode.MICROSERVICE,
                    null,
                    true,
                    false,
                    layout.positions().get(LayoutKeys.msKey(m.id())),
                    new MicroserviceNodeData(
                            m.id(),
                            m.name(),
                            m.description(),
                            m.specCardId(),
                            card == null ? null : card.title(),
                            cardCollapsed,
                            m.team(),
                            m.repositoryUrl(),
                            m.docsUrl())));
            if (card != null) {
                cardOwner.put(card.id(), m.id());
            }
        }
        for (SpecCardView c : cardsById.values()) {
            UUID owner = cardOwner.get(c.id());
            if (owner == null) {
                nodes.add(new CanvasNode(
                        LayoutKeys.cardKey(c.id()),
                        CanvasNode.SPEC_CARD,
                        null,
                        true,
                        false,
                        layout.positions().get(LayoutKeys.cardKey(c.id())),
                        cardData(c, null)));
            } else {
                // a collapsed docked card is still sent, hidden: its endpoints stay available (FR-014, FR-054)
                nodes.add(new CanvasNode(
                        LayoutKeys.cardKey(c.id()),
                        CanvasNode.SPEC_CARD,
                        LayoutKeys.msKey(owner),
                        false,
                        collapsed.contains(c.id()),
                        DOCK_OFFSET,
                        cardData(c, owner)));
            }
        }

        // target microservice → match keys of its linked card's endpoints, for FR-055
        Map<UUID, Set<String>> targetEndpoints = new HashMap<>();
        for (MicroserviceView m : allMicroservices) {
            SpecCardView card = m.specCardId() == null ? null : cardsById.get(m.specCardId());
            if (card != null) {
                Set<String> keys = new HashSet<>();
                card.endpoints()
                        .forEach(e ->
                                keys.add(ConnectionEndpoints.matchKey(e.method().name(), e.path())));
                targetEndpoints.put(m.id(), keys);
            }
        }
        List<CanvasEdge> edges = new ArrayList<>();
        List<ConnectionDto> dangling = new ArrayList<>();
        List<ConnectionView> all = new ArrayList<>(connections.findAll());
        all.sort(Comparator.comparing((ConnectionView v) -> v.key().source())
                .thenComparing(v -> v.key().target())
                .thenComparing(v -> v.key().name()));
        for (ConnectionView v : all) {
            ConnectionDto f = v.connection();
            if (f.dangling()) {
                dangling.add(f);
            } else {
                edges.add(new CanvasEdge(
                        "connection:" + f.id(),
                        LayoutKeys.msKey(f.sourceId()),
                        LayoutKeys.msKey(f.targetId()),
                        CanvasEdge.TYPE,
                        new ConnectionEdgeData(
                                f.id(),
                                f.name(),
                                f.kind(),
                                f.description(),
                                f.flowId(),
                                f.endpoint(),
                                endpointMissing(f, targetEndpoints),
                                f.step())));
            }
        }
        List<FlowDto> flowDtos = flows.findAllOrdered();
        return new CanvasResponse(
                List.copyOf(nodes),
                List.copyOf(edges),
                List.copyOf(dangling),
                List.copyOf(flowDtos),
                notes.list(),
                revision.current(),
                layout.version());
    }

    @Override
    @Transactional(readOnly = true)
    public BaselineIndex index() {
        Map<UUID, String> ms = new HashMap<>();
        Map<UUID, UUID> linked = new HashMap<>();
        for (MicroserviceView m : microservices.findAll()) {
            ms.put(m.id(), m.nameNormalized());
            if (m.specCardId() != null) {
                linked.put(m.id(), m.specCardId());
            }
        }
        Map<UUID, String> hashes = new HashMap<>();
        Set<String> endpointKeys = new HashSet<>();
        for (SpecCardView c : cards.findAll()) {
            hashes.put(c.id(), c.contentHash());
            c.endpoints().forEach(e -> endpointKeys.add(e.endpointKey()));
        }
        Set<UUID> flowIds = new HashSet<>();
        flows.findAllOrdered().forEach(g -> flowIds.add(g.id()));
        Map<UUID, BaselineIndex.ConnectionRef> connectionRefs = new HashMap<>();
        for (ConnectionView v : connections.findAll()) {
            ConnectionKey k = v.key();
            connectionRefs.put(v.connection().id(), new BaselineIndex.ConnectionRef(k.source(), k.target(), k.name()));
        }
        return new BaselineIndex(
                Map.copyOf(ms),
                Map.copyOf(linked),
                Map.copyOf(hashes),
                Set.copyOf(endpointKeys),
                Map.copyOf(connectionRefs),
                Set.copyOf(flowIds),
                revision.current());
    }

    private static boolean endpointMissing(ConnectionDto f, Map<UUID, Set<String>> targetEndpoints) {
        if (f.endpoint() == null) {
            return false;
        }
        Set<String> keys = targetEndpoints.get(f.targetId());
        return keys != null && !keys.contains(ConnectionEndpoints.matchKey(f.endpoint()));
    }

    private static SpecCardNodeData cardData(SpecCardView c, UUID owner) {
        List<EndpointDto> endpoints = new ArrayList<>();
        for (ParsedEndpoint e : c.endpoints()) {
            endpoints.add(EndpointDto.from(e));
        }
        return new SpecCardNodeData(
                c.id(),
                c.title(),
                c.apiVersion(),
                c.specFormat(),
                c.sourceType(),
                c.sourceUrl(),
                c.contentHash(),
                c.lastSyncAt(),
                c.lastSyncStatus(),
                List.copyOf(endpoints),
                owner);
    }
}
