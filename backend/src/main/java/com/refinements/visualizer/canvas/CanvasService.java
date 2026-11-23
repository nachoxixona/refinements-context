package com.refinements.visualizer.canvas;

import com.refinements.visualizer.canvas.dto.CanvasEdge;
import com.refinements.visualizer.canvas.dto.CanvasNode;
import com.refinements.visualizer.canvas.dto.CanvasResponse;
import com.refinements.visualizer.canvas.dto.ConnectionEdgeData;
import com.refinements.visualizer.canvas.dto.EndpointDto;
import com.refinements.visualizer.canvas.dto.MicroserviceNodeData;
import com.refinements.visualizer.canvas.dto.SpecCardNodeData;
import com.refinements.visualizer.card.SpecCard;
import com.refinements.visualizer.card.SpecCardRepository;
import com.refinements.visualizer.common.XYPosition;
import com.refinements.visualizer.connection.Connection;
import com.refinements.visualizer.connection.ConnectionEndpoints;
import com.refinements.visualizer.connection.ConnectionRepository;
import com.refinements.visualizer.connection.FlowRepository;
import com.refinements.visualizer.connection.dto.ConnectionDto;
import com.refinements.visualizer.connection.dto.FlowDto;
import com.refinements.visualizer.ingestion.model.ParsedEndpoint;
import com.refinements.visualizer.layout.BaselineLayout;
import com.refinements.visualizer.layout.BaselineLayoutRepository;
import com.refinements.visualizer.microservice.Microservice;
import com.refinements.visualizer.microservice.MicroserviceRepository;
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

/** Builds {@code GET /api/canvas} (data-model.md §1–§5, research R8). */
@Service
public class CanvasService {

    /** Relative position of a docked card: just below its microservice node. */
    public static final XYPosition DOCK_OFFSET = new XYPosition(0.0, 84.0);

    private final MicroserviceRepository microservices;
    private final SpecCardRepository cards;
    private final ConnectionRepository connections;
    private final FlowRepository flows;
    private final BaselineLayoutRepository layouts;
    private final WorkspaceRevision revision;
    private final com.refinements.visualizer.note.NoteService notes;

    public CanvasService(
            MicroserviceRepository microservices,
            SpecCardRepository cards,
            ConnectionRepository connections,
            FlowRepository flows,
            BaselineLayoutRepository layouts,
            WorkspaceRevision revision,
            com.refinements.visualizer.note.NoteService notes) {
        this.notes = notes;
        this.microservices = microservices;
        this.cards = cards;
        this.connections = connections;
        this.flows = flows;
        this.layouts = layouts;
        this.revision = revision;
    }

    @Transactional(readOnly = true)
    public CanvasResponse canvas() {
        BaselineLayout layout = layouts.getSingleton();
        Set<UUID> collapsed = new HashSet<>(layout.getCollapsedCards());
        Map<UUID, SpecCard> cardsById = new LinkedHashMap<>();
        List<SpecCard> allCards = new ArrayList<>(cards.findAll());
        allCards.sort(Comparator.comparing(SpecCard::getCreatedAt).thenComparing(SpecCard::getId));
        allCards.forEach(c -> cardsById.put(c.getId(), c));

        List<CanvasNode> nodes = new ArrayList<>();
        Map<UUID, UUID> cardOwner = new HashMap<>();
        for (Microservice m : microservices.findAllByOrderByNameNormalizedAsc()) {
            SpecCard card = m.getSpecCardId() == null ? null : cardsById.get(m.getSpecCardId());
            boolean cardCollapsed = card != null && collapsed.contains(card.getId());
            nodes.add(new CanvasNode(
                    BaselineLayout.msKey(m.getId()),
                    CanvasNode.MICROSERVICE,
                    null,
                    true,
                    false,
                    layout.getPositions().get(BaselineLayout.msKey(m.getId())),
                    new MicroserviceNodeData(
                            m.getId(),
                            m.getName(),
                            m.getDescription(),
                            m.getSpecCardId(),
                            card == null ? null : card.getTitle(),
                            cardCollapsed,
                            m.getTeam(),
                            m.getRepositoryUrl(),
                            m.getDocsUrl())));
            if (card != null) {
                cardOwner.put(card.getId(), m.getId());
            }
        }
        for (SpecCard c : cardsById.values()) {
            UUID owner = cardOwner.get(c.getId());
            if (owner == null) {
                nodes.add(new CanvasNode(
                        BaselineLayout.cardKey(c.getId()),
                        CanvasNode.SPEC_CARD,
                        null,
                        true,
                        false,
                        layout.getPositions().get(BaselineLayout.cardKey(c.getId())),
                        cardData(c, null)));
            } else {
                // a collapsed docked card is still sent, hidden: its endpoints stay available (FR-014, FR-054)
                nodes.add(new CanvasNode(
                        BaselineLayout.cardKey(c.getId()),
                        CanvasNode.SPEC_CARD,
                        BaselineLayout.msKey(owner),
                        false,
                        collapsed.contains(c.getId()),
                        DOCK_OFFSET,
                        cardData(c, owner)));
            }
        }

        // target microservice → match keys of its linked card's endpoints, for FR-055
        Map<UUID, Set<String>> targetEndpoints = new HashMap<>();
        for (Microservice m : microservices.findAll()) {
            SpecCard card = m.getSpecCardId() == null ? null : cardsById.get(m.getSpecCardId());
            if (card != null) {
                Set<String> keys = new HashSet<>();
                card.getEndpoints()
                        .forEach(e ->
                                keys.add(ConnectionEndpoints.matchKey(e.method().name(), e.path())));
                targetEndpoints.put(m.getId(), keys);
            }
        }
        List<CanvasEdge> edges = new ArrayList<>();
        List<ConnectionDto> dangling = new ArrayList<>();
        List<Connection> all = new ArrayList<>(connections.findAll());
        all.sort(Comparator.comparing(Connection::getSourceNameNormalized)
                .thenComparing(Connection::getTargetNameNormalized)
                .thenComparing(Connection::getNameNormalized));
        for (Connection f : all) {
            if (f.isDangling()) {
                dangling.add(ConnectionDto.from(f));
            } else {
                edges.add(new CanvasEdge(
                        "connection:" + f.getId(),
                        BaselineLayout.msKey(f.getSourceId()),
                        BaselineLayout.msKey(f.getTargetId()),
                        CanvasEdge.TYPE,
                        new ConnectionEdgeData(
                                f.getId(),
                                f.getName(),
                                f.getKind(),
                                f.getDescription(),
                                f.getFlowId(),
                                f.getEndpoint(),
                                endpointMissing(f, targetEndpoints),
                                f.getStep())));
            }
        }
        List<FlowDto> flowDtos = flows.findAllByOrderByNameNormalizedAsc().stream()
                .map(FlowDto::from)
                .toList();
        return new CanvasResponse(
                List.copyOf(nodes),
                List.copyOf(edges),
                List.copyOf(dangling),
                flowDtos,
                notes.list(),
                revision.current(),
                layout.getVersion());
    }

    @Transactional(readOnly = true)
    public BaselineIndex index() {
        Map<UUID, String> ms = new HashMap<>();
        Map<UUID, UUID> linked = new HashMap<>();
        for (Microservice m : microservices.findAll()) {
            ms.put(m.getId(), m.getNameNormalized());
            if (m.getSpecCardId() != null) {
                linked.put(m.getId(), m.getSpecCardId());
            }
        }
        Map<UUID, String> hashes = new HashMap<>();
        Set<String> endpointKeys = new HashSet<>();
        for (SpecCard c : cards.findAll()) {
            hashes.put(c.getId(), c.getContentHash());
            c.getEndpoints().forEach(e -> endpointKeys.add(e.endpointKey()));
        }
        Set<UUID> flowIds = new HashSet<>();
        flows.findAll().forEach(g -> flowIds.add(g.getId()));
        Map<UUID, BaselineIndex.ConnectionRef> connectionRefs = new HashMap<>();
        for (Connection f : connections.findAll()) {
            connectionRefs.put(
                    f.getId(),
                    new BaselineIndex.ConnectionRef(
                            f.getSourceNameNormalized(), f.getTargetNameNormalized(), f.getNameNormalized()));
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

    private static boolean endpointMissing(Connection f, Map<UUID, Set<String>> targetEndpoints) {
        if (f.getEndpoint() == null) {
            return false;
        }
        Set<String> keys = targetEndpoints.get(f.getTargetId());
        return keys != null && !keys.contains(ConnectionEndpoints.matchKey(f.getEndpoint()));
    }

    private static SpecCardNodeData cardData(SpecCard c, UUID owner) {
        List<EndpointDto> endpoints = new ArrayList<>();
        for (ParsedEndpoint e : c.getEndpoints()) {
            endpoints.add(EndpointDto.from(e));
        }
        return new SpecCardNodeData(
                c.getId(),
                c.getTitle(),
                c.getApiVersion(),
                c.getSpecFormat(),
                c.getSourceType(),
                c.getSourceUrl(),
                c.getContentHash(),
                c.getLastSyncAt(),
                c.getLastSyncStatus(),
                List.copyOf(endpoints),
                owner);
    }
}
