package com.refinements.visualizer.canvas.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.refinements.visualizer.canvas.domain.CanvasNode;
import com.refinements.visualizer.canvas.domain.CanvasResponse;
import com.refinements.visualizer.card.SourceType;
import com.refinements.visualizer.card.SpecCardApi;
import com.refinements.visualizer.card.SpecCardView;
import com.refinements.visualizer.card.domain.SpecCard;
import com.refinements.visualizer.card.model.SpecFormat;
import com.refinements.visualizer.connection.ConnectionApi;
import com.refinements.visualizer.connection.ConnectionKind;
import com.refinements.visualizer.connection.FlowApi;
import com.refinements.visualizer.connection.KeyedConnection;
import com.refinements.visualizer.connection.domain.Connection;
import com.refinements.visualizer.layout.LayoutApi;
import com.refinements.visualizer.layout.LayoutView;
import com.refinements.visualizer.microservice.MicroserviceApi;
import com.refinements.visualizer.microservice.MicroserviceView;
import com.refinements.visualizer.microservice.domain.Microservice;
import com.refinements.visualizer.note.NoteApi;
import com.refinements.visualizer.workspace.WorkspaceRevision;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** research R8: docked vs. unlinked cards, collapsed cards, dangling connections, parallel connections. */
class CanvasServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    private final MicroserviceApi microservices = mock(MicroserviceApi.class);
    private final SpecCardApi cards = mock(SpecCardApi.class);
    private final ConnectionApi connections = mock(ConnectionApi.class);
    private final FlowApi flows = mock(FlowApi.class);
    private final LayoutApi layouts = mock(LayoutApi.class);
    private final WorkspaceRevision revision = mock(WorkspaceRevision.class);
    private final CanvasService service =
            new CanvasService(microservices, cards, connections, flows, layouts, revision, notes());

    private static NoteApi notes() {
        NoteApi n = mock(NoteApi.class);
        org.mockito.Mockito.when(n.list()).thenReturn(List.of());
        return n;
    }

    private static SpecCard card(String title) {
        SpecCard c = SpecCard.create(java.util.UUID.randomUUID(), SourceType.PASTE, null, NOW);
        c.applyParsed(title, "1", SpecFormat.OPENAPI_3_0, "x", "h-" + title, List.of(), NOW);
        return c;
    }

    private static Connection connection(Microservice s, Microservice t, String name) {
        Connection f = new Connection(name, ConnectionKind.EVENT, null, NOW);
        f.setSource(s == null ? null : s.getId(), s == null ? "Gone" : s.getName(), NOW);
        f.setTarget(t.getId(), t.getName(), NOW);
        return f;
    }

    private static LayoutView layout(List<java.util.UUID> collapsed) {
        return new LayoutView(Map.of(), collapsed, 0);
    }

    // fixtures are built as entities and handed to the module APIs as views (specs/005: canvas reads APIs only)

    private static MicroserviceView view(Microservice m) {
        return new MicroserviceView(
                m.getId(),
                m.getName(),
                m.getNameNormalized(),
                m.getDescription(),
                m.getSpecCardId(),
                m.getTeam(),
                m.getRepositoryUrl(),
                m.getDocsUrl());
    }

    private static SpecCardView view(SpecCard c) {
        return new SpecCardView(
                c.getId(),
                c.getTitle(),
                c.getApiVersion(),
                c.getSpecFormat(),
                c.getSourceType(),
                c.getSourceUrl(),
                c.getContentHash(),
                c.getLastSyncAt(),
                c.getLastSyncStatus(),
                c.getEndpoints(),
                c.getCreatedAt());
    }

    private static KeyedConnection view(Connection f) {
        return new KeyedConnection(f.view(), f.key());
    }

    @Test
    void buildsDockedUnlinkedAndCollapsedCardsAndSeparatesDanglingConnections() throws Exception {
        Microservice orders = Microservice.create("Orders", null, NOW);
        Microservice billing = Microservice.create("Billing", null, NOW);
        Microservice payments = Microservice.create("Payments", null, NOW);
        SpecCard docked = card("Orders API");
        SpecCard collapsed = card("Billing API");
        SpecCard loose = card("Loose API");
        orders.link(docked.getId(), NOW);
        billing.link(collapsed.getId(), NOW);

        when(microservices.findAllOrdered()).thenReturn(List.of(view(billing), view(orders), view(payments)));
        when(cards.findAll()).thenReturn(List.of(view(docked), view(collapsed), view(loose)));
        when(connections.findAll())
                .thenReturn(List.of(
                        view(connection(orders, billing, "A")),
                        view(connection(orders, billing, "B")),
                        view(connection(billing, orders, "C")),
                        view(connection(null, payments, "Lost"))));
        when(layouts.current()).thenReturn(layout(List.of(collapsed.getId())));
        when(revision.current()).thenReturn(7L);

        CanvasResponse c = service.canvas();
        assertThat(c.nodes())
                .extracting(CanvasNode::id)
                .contains("ms:" + orders.getId(), "card:" + docked.getId(), "card:" + loose.getId())
                .contains("card:" + collapsed.getId());
        assertThat(c.nodes().stream()
                        .filter(n -> n.id().equals("card:" + collapsed.getId()))
                        .findFirst()
                        .orElseThrow()
                        .hidden())
                .as("collapsed docked cards are sent hidden")
                .isTrue();
        assertThat(c.nodes().stream().filter(n -> !n.id().equals("card:" + collapsed.getId())))
                .noneMatch(CanvasNode::hidden);
        CanvasNode dockedNode = c.nodes().stream()
                .filter(n -> n.id().equals("card:" + docked.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(dockedNode.parentId()).isEqualTo("ms:" + orders.getId());
        assertThat(dockedNode.draggable()).isFalse();
        assertThat(dockedNode.position()).isEqualTo(CanvasService.DOCK_OFFSET);
        CanvasNode looseNode = c.nodes().stream()
                .filter(n -> n.id().equals("card:" + loose.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(looseNode.parentId()).isNull();
        assertThat(c.nodes().indexOf(dockedNode))
                .as("parents come before their children")
                .isGreaterThan(c.nodes().stream().map(CanvasNode::id).toList().indexOf("ms:" + orders.getId()));

        assertThat(c.edges()).hasSize(3);
        assertThat(c.edges()).extracting(e -> e.data().name()).containsExactlyInAnyOrder("A", "B", "C");
        assertThat(c.danglingConnections()).singleElement().satisfies(f -> {
            assertThat(f.name()).isEqualTo("Lost");
            assertThat(f.sourceName()).isEqualTo("Gone");
        });
        assertThat(c.revision()).isEqualTo(7L);
    }
}
