import type { CanvasResponse, Endpoint, Field, ConnectionKind } from '../../src/api/types';
import { normalizeName } from '../../src/canvas/normalize';
import { baselineView, type GraphView, type ViewEdge, type ViewNode } from '../../src/canvas/viewModel';

export function field(path: string, location: Field['location'], type = 'string'): Field {
  const name = path.split('.').pop()!.replace('[]', '');
  return { path, name, normalizedName: normalizeName(name), type, format: null, location, required: false, variant: null, truncated: false };
}

export function endpoint(prefix: string, method: Endpoint['method'], path: string, inputs: Field[] = [], outputs: Field[] = []): Endpoint {
  return { endpointKey: `${prefix}:${method} ${path.replace(/\{[^}]*\}/g, '{}')}`, method, path, operationId: null, summary: null, inputs, outputs };
}

export interface MsSpec {
  id: string;
  name?: string;
  position?: { x: number; y: number } | null;
  /** id of the card linked to it */
  card?: string;
  collapsed?: boolean;
}

export interface CardSpec {
  id: string;
  title?: string;
  endpoints?: Endpoint[];
  position?: { x: number; y: number } | null;
}

export interface ConnectionSpec {
  id: string;
  source: string;
  target: string;
  name?: string;
  kind?: ConnectionKind | null;
}

/** A CanvasResponse: microservices `ms:{id}`, cards `card:{id}` (docked when a microservice links them), connections. */
export function canvas(microservices: MsSpec[], cards: CardSpec[] = [], connections: ConnectionSpec[] = []): CanvasResponse {
  const owner = new Map(microservices.filter((m) => m.card).map((m) => [m.card!, m]));
  const msNodes = microservices.map((m) => {
    const card = m.card ? cards.find((c) => c.id === m.card) : undefined;
    return {
      id: `ms:${m.id}`,
      type: 'microservice' as const,
      parentId: null,
      draggable: true,
      hidden: false,
      position: m.position ?? null,
      data: {
        microserviceId: m.id,
        name: m.name ?? m.id,
        description: null,
        specCardId: m.card ?? null,
        cardTitle: card ? (card.title ?? card.id) : null,
        cardCollapsed: Boolean(m.collapsed),
      },
    };
  });
  const cardNodes = cards.map((c) => {
      const o = owner.get(c.id);
      return {
        id: `card:${c.id}`,
        type: 'specCard' as const,
        parentId: o ? `ms:${o.id}` : null,
        draggable: !o,
        hidden: Boolean(o?.collapsed),
        position: o ? { x: 0, y: 84 } : (c.position ?? null),
        data: {
          cardId: c.id,
          title: c.title ?? c.id,
          apiVersion: '1.0',
          specFormat: 'OPENAPI_3_0' as const,
          sourceType: 'PASTE' as const,
          sourceUrl: null,
          contentHash: 'h-' + c.id,
          lastSyncAt: '2026-11-22T10:00:00Z',
          lastSyncStatus: 'OK' as const,
          endpoints: c.endpoints ?? [],
          linkedMicroserviceId: o ? o.id : null,
        },
      };
    });
  return {
    nodes: [...msNodes, ...cardNodes],
    edges: connections.map((f) => ({
      id: `connection:${f.id}`,
      source: `ms:${f.source}`,
      target: `ms:${f.target}`,
      type: 'connection' as const,
      data: { connectionId: f.id, name: f.name ?? f.id, kind: f.kind ?? null, description: null },
    })),
    danglingConnections: [],
    flows: [],
    notes: [],
    revision: 1,
    layoutVersion: 1,
  };
}

export function view(c: CanvasResponse): GraphView {
  return baselineView(c);
}

export function deepFreeze<T>(o: T): T {
  if (o && typeof o === 'object' && !Object.isFrozen(o)) {
    Object.freeze(o);
    for (const v of Object.values(o as Record<string, unknown>)) deepFreeze(v);
  }
  return o;
}

export type { ViewEdge, ViewNode };
