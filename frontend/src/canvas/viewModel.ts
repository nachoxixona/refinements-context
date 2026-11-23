import type {
  CanvasResponse,
  Endpoint,
  Field,
  Connection,
  Flow,
  ConnectionKind,
  MicroserviceNodeData,
  SpecCardNodeData,
  XYPosition,
} from '../api/types';

/** A field row; `proposed` fields come from a Draft Scenario overlay. */
export type ViewField = Field & { proposed?: boolean; orphaned?: boolean };

/** An endpoint row; `proposed` rows come from a Draft Scenario overlay. */
export type ViewEndpoint = Omit<Endpoint, 'inputs' | 'outputs'> & {
  inputs: ViewField[];
  outputs: ViewField[];
  proposed?: boolean;
  orphaned?: boolean;
};

/** Draft-mode marks shared by nodes and edges (FR-038, FR-037a). */
export type ProposalMarks = {
  proposed?: boolean;
  /** baseline element proposed for removal */
  removed?: boolean;
  orphaned?: boolean;
};

export type MicroserviceView = ProposalMarks & {
  kind: 'microservice';
  /** Baseline microservice id; null for a proposed one. */
  microserviceId: string | null;
  /** The key a scenario uses for this microservice: the baseline id or the proposed tempKey. */
  ref: string;
  name: string;
  description: string | null;
  /** Node id of the docked card, when one is shown. */
  cardNodeId: string | null;
  cardTitle: string | null;
  cardCollapsed: boolean;
  /** Baseline card id linked to this microservice (even when collapsed). */
  specCardId: string | null;
  /** the link to its card is proposed for removal */
  linkRemoved?: boolean;
  /** ownership (spec 004 FR-013) */
  team?: string | null;
  repositoryUrl?: string | null;
  docsUrl?: string | null;
};

export type CardView = ProposalMarks & {
  kind: 'card';
  /** Baseline card id; null for a proposed card. */
  cardId: string | null;
  /** Prefix of this card's endpoint keys (card id, or the proposed microservice's tempKey). */
  keyPrefix: string;
  title: string;
  apiVersion: string | null;
  endpoints: ViewEndpoint[];
  /** Node id of the microservice it is docked to. */
  ownerId: string | null;
  ownerName: string | null;
  /** docked through a proposed link */
  linkProposed?: boolean;
  /** Present for baseline cards (traceability, sync status). */
  baseline?: Omit<SpecCardNodeData, 'endpoints'>;
};

export type ViewNodeData = MicroserviceView | CardView;

export interface ViewNode {
  id: string;
  position: XYPosition | null;
  parentId: string | null;
  /** a docked card collapsed to its "API" badge: kept for its endpoints, not drawn */
  hidden?: boolean;
  data: ViewNodeData;
}

export type ConnectionValues = {
  name: string;
  kind: ConnectionKind | null;
  description: string | null;
  flowId: string | null;
  /** `METHOD /path` on the target (FR-054) */
  endpoint: string | null;
  /** order within its flow (spec 004 FR-001) */
  step?: number | null;
};

export type ViewEdgeData = ProposalMarks &
  ConnectionValues & {
  /** Baseline connection id, or the proposed connection id. */
  connectionId: string;
  /** A proposed change: the values before it. */
  before?: ConnectionValues;
  /** the target has a spec card without this endpoint (FR-055) */
  endpointMissing?: boolean;
  };

export interface ViewEdge {
  id: string;
  source: string;
  target: string;
  data: ViewEdgeData;
}

export interface GraphView {
  nodes: ViewNode[];
  edges: ViewEdge[];
  dangling: Connection[];
  flows: Flow[];
}

export const isMicroservice = (d: ViewNodeData): d is MicroserviceView => d.kind === 'microservice';
export const isCard = (d: ViewNodeData): d is CardView => d.kind === 'card';

/** Relative position of a docked card below its microservice (matches the backend's DOCK_OFFSET). */
/** `GET /orders/{id}` → `GET /orders/{}`, to compare endpoints like the backend (ConnectionEndpoints.matchKey). */
export function endpointMatchKey(endpoint: string): string {
  const [method = '', ...rest] = endpoint.trim().split(/\s+/);
  return `${method.toUpperCase()} ${rest.join(' ').replace(/\{[^}/]*\}/g, '{}')}`;
}

/** The endpoints a connection to `targetNodeId` can name: those of the target's docked card, as `METHOD /path`. */
export function targetEndpoints(view: GraphView, targetNodeId: string): string[] {
  const target = view.nodes.find((n) => n.id === targetNodeId);
  if (!target || !isMicroservice(target.data) || !target.data.cardNodeId) return [];
  const cardNodeId = target.data.cardNodeId;
  const card = view.nodes.find((n) => n.id === cardNodeId);
  return card && isCard(card.data) ? card.data.endpoints.map((e) => `${e.method} ${e.path}`) : [];
}

export const DOCK_OFFSET: XYPosition = { x: 0, y: 84 };

const viewCache = new WeakMap<CanvasResponse, GraphView>();

/** The baseline canvas as a view. Memoized per response so references stay stable between renders. */
export function baselineView(canvas: CanvasResponse): GraphView {
  const cached = viewCache.get(canvas);
  if (cached) return cached;
  const names = new Map<string, string>();
  for (const n of canvas.nodes) if (n.type === 'microservice') names.set(n.id, (n.data as MicroserviceNodeData).name);
  const dockedCards = new Map<string, string>();
  for (const n of canvas.nodes) if (n.type === 'specCard' && n.parentId) dockedCards.set(n.parentId, n.id);

  const nodes: ViewNode[] = canvas.nodes.map((n) => {
    if (n.type === 'microservice') {
      const d = n.data as MicroserviceNodeData;
      return {
        id: n.id,
        position: n.position ?? null,
        parentId: null,
        data: {
          kind: 'microservice',
          microserviceId: d.microserviceId,
          ref: d.microserviceId,
          name: d.name,
          description: d.description ?? null,
          cardNodeId: dockedCards.get(n.id) ?? null,
          cardTitle: d.cardTitle ?? null,
          cardCollapsed: d.cardCollapsed,
          specCardId: d.specCardId ?? null,
          team: d.team ?? null,
          repositoryUrl: d.repositoryUrl ?? null,
          docsUrl: d.docsUrl ?? null,
        },
      };
    }
    const d = n.data as SpecCardNodeData;
    const { endpoints, ...rest } = d;
    return {
      id: n.id,
      position: n.position ?? null,
      parentId: n.parentId ?? null,
      hidden: n.hidden || undefined,
      data: {
        kind: 'card',
        cardId: d.cardId,
        keyPrefix: d.cardId,
        title: d.title,
        apiVersion: d.apiVersion ?? null,
        endpoints,
        ownerId: n.parentId ?? null,
        ownerName: n.parentId ? (names.get(n.parentId) ?? null) : null,
        baseline: rest,
      },
    };
  });
  const edges: ViewEdge[] = canvas.edges.map((e) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    data: {
      connectionId: e.data.connectionId,
      name: e.data.name,
      kind: e.data.kind ?? null,
      description: e.data.description ?? null,
      flowId: e.data.flowId ?? null,
      endpoint: e.data.endpoint ?? null,
      endpointMissing: e.data.endpointMissing || undefined,
      step: e.data.step ?? null,
    },
  }));
  const view: GraphView = { nodes, edges, dangling: canvas.danglingConnections, flows: canvas.flows };
  viewCache.set(canvas, view);
  return view;
}

export const EMPTY_VIEW: GraphView = { nodes: [], edges: [], dangling: [], flows: [] };
