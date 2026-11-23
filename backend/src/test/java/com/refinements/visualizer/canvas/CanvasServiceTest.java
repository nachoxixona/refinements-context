package com.refinements.visualizer.canvas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.refinements.visualizer.canvas.dto.CanvasNode;
import com.refinements.visualizer.canvas.dto.CanvasResponse;
import com.refinements.visualizer.card.SourceType;
import com.refinements.visualizer.card.SpecCard;
import com.refinements.visualizer.card.SpecCardRepository;
import com.refinements.visualizer.connection.Connection;
import com.refinements.visualizer.connection.ConnectionKind;
import com.refinements.visualizer.connection.ConnectionRepository;
import com.refinements.visualizer.ingestion.model.SpecFormat;
import com.refinements.visualizer.layout.BaselineLayout;
import com.refinements.visualizer.layout.BaselineLayoutRepository;
import com.refinements.visualizer.microservice.Microservice;
import com.refinements.visualizer.microservice.MicroserviceRepository;
import com.refinements.visualizer.workspace.WorkspaceRevision;
import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** research R8: docked vs. unlinked cards, collapsed cards, dangling connections, parallel connections. */
class CanvasServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    private final MicroserviceRepository microservices = mock(MicroserviceRepository.class);
    private final SpecCardRepository cards = mock(SpecCardRepository.class);
    private final ConnectionRepository connections = mock(ConnectionRepository.class);
    private final com.refinements.visualizer.connection.FlowRepository flows =
            mock(com.refinements.visualizer.connection.FlowRepository.class);
    private final BaselineLayoutRepository layouts = mock(BaselineLayoutRepository.class);
    private final WorkspaceRevision revision = mock(WorkspaceRevision.class);
    private final CanvasService service =
            new CanvasService(microservices, cards, connections, flows, layouts, revision, notes());

    private static com.refinements.visualizer.note.NoteService notes() {
        com.refinements.visualizer.note.NoteService n = mock(com.refinements.visualizer.note.NoteService.class);
        org.mockito.Mockito.when(n.list()).thenReturn(List.of());
        return n;
    }

    private static SpecCard card(String title) {
        SpecCard c = new SpecCard(SourceType.PASTE, null, NOW);
        c.applyParsed(title, "1", SpecFormat.OPENAPI_3_0, "x", "h-" + title, List.of(), NOW);
        return c;
    }

    private static Connection connection(Microservice s, Microservice t, String name) {
        Connection f = new Connection(name, ConnectionKind.EVENT, null, NOW);
        f.setSource(s == null ? null : s.getId(), s == null ? "Gone" : s.getName(), NOW);
        f.setTarget(t.getId(), t.getName(), NOW);
        return f;
    }

    private static BaselineLayout layout(List<java.util.UUID> collapsed) throws Exception {
        Constructor<BaselineLayout> ctor = BaselineLayout.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        BaselineLayout l = ctor.newInstance();
        l.replace(Map.of(), collapsed, NOW);
        return l;
    }

    @Test
    void buildsDockedUnlinkedAndCollapsedCardsAndSeparatesDanglingConnections() throws Exception {
        Microservice orders = new Microservice("Orders", null, NOW);
        Microservice billing = new Microservice("Billing", null, NOW);
        Microservice payments = new Microservice("Payments", null, NOW);
        SpecCard docked = card("Orders API");
        SpecCard collapsed = card("Billing API");
        SpecCard loose = card("Loose API");
        orders.link(docked.getId(), NOW);
        billing.link(collapsed.getId(), NOW);

        when(microservices.findAllByOrderByNameNormalizedAsc()).thenReturn(List.of(billing, orders, payments));
        when(cards.findAll()).thenReturn(List.of(docked, collapsed, loose));
        when(connections.findAll())
                .thenReturn(List.of(
                        connection(orders, billing, "A"),
                        connection(orders, billing, "B"),
                        connection(billing, orders, "C"),
                        connection(null, payments, "Lost")));
        when(layouts.getSingleton()).thenReturn(layout(List.of(collapsed.getId())));
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
