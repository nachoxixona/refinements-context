import type { Orphan, ProposedEndpoint, ScenarioContent, SimpleField } from '../api/types';
import { endpointKeyOf, normalizeName } from '../canvas/normalize';
import {
  DOCK_OFFSET,
  endpointMatchKey,
  isCard,
  isMicroservice,
  type CardView,
  type GraphView,
  type MicroserviceView,
  type ViewEdge,
  type ViewEndpoint,
  type ViewField,
  type ViewNode,
} from '../canvas/viewModel';

/** `address.city` → `city`; `items[].discount` → `discount`. */
export function leafName(path: string): string {
  const leaf = path.slice(path.lastIndexOf('.') + 1);
  return leaf.replace(/\[\]$/, '');
}

/** The path of the field that contains `path`: `items[].discount` → `items`; top level → null. */
export function parentPath(path: string): string | null {
  const dot = path.lastIndexOf('.');
  return dot < 0 ? null : path.slice(0, dot).replace(/\[\]$/, '');
}

/** True for the field itself and everything nested under it. */
export function isSelfOrDescendant(path: string, ancestor: string): boolean {
  return path === ancestor || path.startsWith(ancestor + '.') || path.startsWith(ancestor + '[].');
}

/**
 * Inserts proposed fields into a field list right after their parent's last descendant, so nested proposals
 * show under the object they belong to. Top-level fields go at the end.
 */
export function mergeFields(base: readonly ViewField[], extra: readonly ViewField[]): ViewField[] {
  const out = [...base];
  for (const f of extra) {
    const parent = parentPath(f.path);
    let at = out.length;
    if (parent !== null) {
      const last = out.reduce((acc, g, i) => (isSelfOrDescendant(g.path, parent) ? i : acc), -1);
      if (last >= 0) at = last + 1;
    }
    out.splice(at, 0, f);
  }
  return out;
}

function toField(f: SimpleField, location: 'BODY' | 'RESPONSE', orphaned = false): ViewField {
  return {
    path: f.name,
    name: leafName(f.name),
    normalizedName: normalizeName(leafName(f.name)),
    type: f.type && f.type.trim() !== '' ? f.type : 'unknown',
    format: null,
    location,
    required: false,
    variant: null,
    truncated: false,
    proposed: true,
    orphaned,
  };
}

/** A proposed endpoint in the same row shape as a backend endpoint. */
export function toViewEndpoint(prefix: string, ep: ProposedEndpoint, orphaned = false): ViewEndpoint {
  return {
    endpointKey: endpointKeyOf(prefix, ep.method, ep.path),
    method: ep.method,
    path: ep.path,
    operationId: null,
    summary: null,
    inputs: mergeFields([], (ep.inputs ?? []).map((f) => toField(f, 'BODY'))),
    outputs: mergeFields([], (ep.outputs ?? []).map((f) => toField(f, 'RESPONSE'))),
    proposed: true,
    orphaned,
  };
}

export function isEmptyDraft(draft: ScenarioContent): boolean {
  const r = draft.removals;
  return (
    draft.proposedMicroservices.length === 0 &&
    draft.proposedLinks.length === 0 &&
    draft.proposedConnections.length === 0 &&
    draft.addedEndpoints.length === 0 &&
    draft.addedFields.length === 0 &&
    r.connections.length + r.microservices.length + r.links.length === 0 &&
    draft.connectionChanges.length === 0 &&
    Object.keys(draft.positions).length === 0
  );
}

/** Node id of a scenario microservice reference: a baseline id → `ms:{id}`, a tempKey stays as is. */
export const nodeIdOf = (ref: string) => (ref.startsWith('proposed:') ? ref : `ms:${ref}`);
/** Node id of a proposed microservice's card. */
export const proposedCardNodeId = (tempKey: string) => `card:${tempKey}`;

let last: { baseline: GraphView; draft: ScenarioContent; orphans: readonly Orphan[]; result: GraphView } | null = null;

/**
 * baseline ⊕ draft (research R9, Principle IV). Pure: returns new node and edge arrays and never mutates the
 * baseline; untouched baseline nodes keep their object identity. Memoized on its last arguments.
 */
export function applyOverlay(baseline: GraphView, draft: ScenarioContent, orphans: readonly Orphan[] = []): GraphView {
  if (last && last.baseline === baseline && last.draft === draft && last.orphans === orphans) return last.result;
  const result = compute(baseline, draft, orphans);
  last = { baseline, draft, orphans, result };
  return result;
}

function compute(baseline: GraphView, draft: ScenarioContent, orphans: readonly Orphan[]): GraphView {
  if (isEmptyDraft(draft)) return baseline;

  const orphanUsers = new Set<string>();
  for (const o of orphans) for (const u of o.usedBy) orphanUsers.add(u);
  const removedMs = new Set(draft.removals.microservices);
  const removedLinks = new Set([...draft.removals.links, ...draft.removals.microservices]);
  const removedConnections = new Set(draft.removals.connections);
  const changes = new Map(draft.connectionChanges.map((c) => [c.connectionId, c]));

  // added endpoints and fields on baseline cards, grouped by card id
  const addedEps = new Map<string, ViewEndpoint[]>();
  draft.addedEndpoints.forEach((a, i) => {
    const list = addedEps.get(a.cardId) ?? [];
    list.push(toViewEndpoint(a.cardId, a.endpoint, orphanUsers.has(`addedEndpoints[${i}]`)));
    addedEps.set(a.cardId, list);
  });
  const fieldsByEndpoint = new Map<string, { inputs: ViewField[]; outputs: ViewField[] }>();
  draft.addedFields.forEach((f, i) => {
    const entry = fieldsByEndpoint.get(f.endpointKey) ?? { inputs: [], outputs: [] };
    const orphaned = orphanUsers.has(`addedFields[${i}]`);
    if (f.direction === 'IN') entry.inputs.push(toField(f.field, 'BODY', orphaned));
    else entry.outputs.push(toField(f.field, 'RESPONSE', orphaned));
    fieldsByEndpoint.set(f.endpointKey, entry);
  });

  // proposed links: card id → owner node id
  const linkedTo = new Map<string, string>();
  draft.proposedLinks.forEach((l) => linkedTo.set(l.cardId, nodeIdOf(l.microservice)));
  const msName = new Map<string, string>();
  for (const n of baseline.nodes) if (isMicroservice(n.data)) msName.set(n.id, n.data.name);
  for (const p of draft.proposedMicroservices) msName.set(p.tempKey, p.name);
  const linkedCardNode = new Map<string, { nodeId: string; title: string }>(); // owner node → card node

  const nodes: ViewNode[] = [];
  for (const n of baseline.nodes) {
    const pos = draft.positions[n.id];
    if (isMicroservice(n.data)) {
      const id = n.data.microserviceId ?? '';
      const removed = removedMs.has(id);
      const linkRemoved = removedLinks.has(id) && n.data.specCardId !== null;
      if (!removed && !linkRemoved && !pos) {
        nodes.push(n);
        continue;
      }
      const data: MicroserviceView = { ...n.data, removed: removed || undefined, linkRemoved: linkRemoved || undefined };
      nodes.push({ ...n, position: pos ?? n.position, data });
      continue;
    }
    if (!isCard(n.data)) continue;
    const cardId = n.data.cardId ?? '';
    const extra = addedEps.get(cardId);
    const touched = n.data.endpoints.some((ep) => fieldsByEndpoint.has(ep.endpointKey));
    const newOwner = linkedTo.get(cardId);
    const ownerRemovedLink = n.data.ownerId !== null && n.parentId !== null && isOwnerLinkRemoved(baseline, n.parentId, removedLinks);
    if (!extra && !touched && !newOwner && !ownerRemovedLink && !pos) {
      nodes.push(n);
      continue;
    }
    let endpoints = n.data.endpoints;
    if (touched) {
      endpoints = endpoints.map((ep) => {
        const f = fieldsByEndpoint.get(ep.endpointKey);
        return f ? { ...ep, inputs: mergeFields(ep.inputs, f.inputs), outputs: mergeFields(ep.outputs, f.outputs) } : ep;
      });
    }
    if (extra) endpoints = [...endpoints, ...extra];
    const data: CardView = { ...n.data, endpoints };
    let parentId = n.parentId;
    let position = pos ?? n.position;
    if (newOwner) {
      parentId = newOwner;
      position = DOCK_OFFSET;
      data.ownerId = newOwner;
      data.ownerName = msName.get(newOwner) ?? null;
      data.linkProposed = true;
      data.orphaned = orphanUsers.has(`proposedLinks[${draft.proposedLinks.findIndex((l) => l.cardId === cardId)}]`) || undefined;
      linkedCardNode.set(newOwner, { nodeId: n.id, title: data.title });
    } else if (ownerRemovedLink) {
      data.removed = true;
    }
    nodes.push({ ...n, parentId, position, data });
  }

  for (const p of draft.proposedMicroservices) {
    const cardNode = p.card ? proposedCardNodeId(p.tempKey) : null;
    const linked = linkedCardNode.get(p.tempKey);
    nodes.push({
      id: p.tempKey,
      position: draft.positions[p.tempKey] ?? null,
      parentId: null,
      data: {
        kind: 'microservice',
        microserviceId: null,
        ref: p.tempKey,
        name: p.name,
        description: p.description ?? null,
        team: p.team ?? null,
        cardNodeId: cardNode ?? linked?.nodeId ?? null,
        cardTitle: p.card?.title ?? linked?.title ?? null,
        cardCollapsed: false,
        specCardId: null,
        proposed: true,
      },
    });
    if (p.card && cardNode) {
      nodes.push({
        id: cardNode,
        position: DOCK_OFFSET,
        parentId: p.tempKey,
        data: {
          kind: 'card',
          cardId: null,
          keyPrefix: p.tempKey,
          title: p.card.title,
          apiVersion: null,
          endpoints: p.card.endpoints.map((ep) => toViewEndpoint(p.tempKey, ep)),
          ownerId: p.tempKey,
          ownerName: p.name,
          proposed: true,
        },
      });
    }
  }
  // baseline microservices that gained a docked card through a proposed link
  for (let i = 0; i < nodes.length; i++) {
    const n = nodes[i]!;
    const linked = linkedCardNode.get(n.id);
    if (linked && isMicroservice(n.data) && n.data.microserviceId !== null) {
      nodes[i] = { ...n, data: { ...n.data, cardNodeId: linked.nodeId, cardTitle: linked.title, cardCollapsed: false } };
    }
  }

  const nodeIds = new Set(nodes.map((n) => n.id));
  const edges: ViewEdge[] = baseline.edges.map((e) => {
    const removed =
      removedConnections.has(e.data.connectionId) ||
      removedMs.has(e.source.replace(/^ms:/, '')) ||
      removedMs.has(e.target.replace(/^ms:/, ''));
    const c = changes.get(e.data.connectionId);
    if (!removed && !c) return e;
    const data = { ...e.data };
    if (removed) data.removed = true;
    if (c) {
      data.before = {
        name: e.data.name,
        kind: e.data.kind,
        description: e.data.description,
        flowId: e.data.flowId,
        endpoint: e.data.endpoint,
        step: e.data.step ?? null,
      };
      if (c.name) data.name = c.name;
      if (c.kind) data.kind = c.kind === 'NONE' ? null : c.kind;
      if (c.description !== undefined && c.description !== null) data.description = c.description;
      if (c.flowId) {
        data.flowId = c.flowId === 'NONE' ? null : c.flowId;
        // a new flow without a proposed step: after its numbered steps (spec 004 FR-002, FR-007)
        if (c.step == null) data.step = null;
      }
      if (c.step != null && data.flowId) data.step = c.step;
      if (c.endpoint) {
        data.endpoint = c.endpoint === 'NONE' ? null : c.endpoint;
        data.endpointMissing = missingIn(nodes, e.target, data.endpoint) || undefined;
      }
    }
    return { ...e, data };
  });
  draft.proposedConnections.forEach((f, i) => {
    const source = nodeIdOf(f.source);
    const target = nodeIdOf(f.target);
    if (!nodeIds.has(source) || !nodeIds.has(target)) return; // orphaned end: listed in the panel, not drawable
    edges.push({
      id: `proposed:${f.id}`,
      source,
      target,
      data: {
        connectionId: f.id,
        name: f.name,
        kind: f.kind ?? null,
        description: f.description ?? null,
        flowId: f.flowId ?? null,
        endpoint: f.endpoint ?? null,
        step: f.flowId ? (f.step ?? null) : null,
        endpointMissing: missingIn(nodes, target, f.endpoint ?? null) || undefined,
        proposed: true,
        orphaned: orphanUsers.has(`proposedConnections[${i}]`) || undefined,
      },
    });
  });

  // React Connection needs parents before their children
  const top = nodes.filter((n) => n.parentId === null);
  const children = nodes.filter((n) => n.parentId !== null && nodeIds.has(n.parentId));
  return { nodes: [...top, ...children], edges, dangling: baseline.dangling, flows: baseline.flows };
}

/** True when `endpoint` is set, the target has a docked card, and the card lacks it (FR-055). */
function missingIn(nodes: readonly ViewNode[], targetId: string, endpoint: string | null): boolean {
  if (!endpoint) return false;
  const target = nodes.find((n) => n.id === targetId);
  if (!target || !isMicroservice(target.data) || !target.data.cardNodeId) return false;
  const cardNodeId = target.data.cardNodeId;
  const card = nodes.find((n) => n.id === cardNodeId);
  if (!card || !isCard(card.data)) return false;
  const key = endpointMatchKey(endpoint);
  return !card.data.endpoints.some((ep) => endpointMatchKey(`${ep.method} ${ep.path}`) === key);
}

function isOwnerLinkRemoved(baseline: GraphView, parentId: string, removedLinks: ReadonlySet<string>): boolean {
  const owner = baseline.nodes.find((n) => n.id === parentId);
  return owner !== undefined && isMicroservice(owner.data) && removedLinks.has(owner.data.microserviceId ?? '');
}
