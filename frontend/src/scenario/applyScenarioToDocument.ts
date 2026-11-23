import type { ConnectionsDocument, ScenarioContent } from '../api/types';
import { normalizeLabel } from '../canvas/normalize';

type DocConnection = ConnectionsDocument['connections'][number];
type DocMicroservice = NonNullable<ConnectionsDocument['microservices']>[number];

/** Baseline microservice id → name, for resolving the ids a scenario uses. */
export type NamesById = ReadonlyMap<string, string>;
/** Flow id → name. */
export type FlowNamesById = ReadonlyMap<string, string>;
/** Baseline connection id → its (source, target, name) in the document. */
export type ConnectionKeysById = ReadonlyMap<string, { source: string; target: string; name: string }>;

const key = (f: { source: string; target: string; name: string }) =>
  `${normalizeLabel(f.source)}|${normalizeLabel(f.target)}|${normalizeLabel(f.name)}`;
const toDocKind = (k: string | null | undefined): DocConnection['kind'] =>
  k === 'REQUEST' ? 'request' : k === 'EVENT' ? 'event' : undefined;

/**
 * The baseline connections document with a scenario applied (FR-043, research R9): proposed microservices and connections
 * added, proposed removals left out, proposed connection changes applied. Pure transformation; the backend
 * validates the result when it is imported.
 */
export function applyScenarioToDocument(
  doc: ConnectionsDocument,
  draft: ScenarioContent,
  names: NamesById,
  connectionKeys: ConnectionKeysById,
  flowNames: FlowNamesById = new Map(),
): ConnectionsDocument {
  const removedMs = new Set(draft.removals.microservices.map((id) => normalizeLabel(names.get(id) ?? '')));
  const removedConnectionKeys = new Set(draft.removals.connections.map((id) => connectionKeys.get(id)).filter(Boolean).map((f) => key(f!)));
  const changes = new Map<string, ScenarioContent['connectionChanges'][number]>();
  for (const c of draft.connectionChanges) {
    const f = connectionKeys.get(c.connectionId);
    if (f) changes.set(key(f), c);
  }

  const microservices: DocMicroservice[] = (doc.microservices ?? []).filter((m) => !removedMs.has(normalizeLabel(m.name)));
  for (const p of draft.proposedMicroservices) {
    const m: DocMicroservice = { name: p.name };
    if (p.description) m.description = p.description;
    microservices.push(m);
  }

  const connections: DocConnection[] = [];
  for (const f of doc.connections) {
    if (removedMs.has(normalizeLabel(f.source)) || removedMs.has(normalizeLabel(f.target))) continue;
    if (removedConnectionKeys.has(key(f))) continue;
    const c = changes.get(key(f));
    if (!c) {
      connections.push(f);
      continue;
    }
    const next: DocConnection = { ...f };
    if (c.name) next.name = c.name;
    if (c.kind) {
      const kind = toDocKind(c.kind === 'NONE' ? null : c.kind);
      if (kind) next.kind = kind;
      else delete next.kind;
    }
    if (c.description !== undefined && c.description !== null) next.description = c.description;
    if (c.flowId) {
      const g = c.flowId === 'NONE' ? undefined : flowNames.get(c.flowId);
      if (g) next.flow = g;
      else delete next.flow;
    }
    if (c.endpoint) {
      if (c.endpoint === 'NONE') delete next.endpoint;
      else next.endpoint = c.endpoint;
    }
    connections.push(next);
  }
  const nameOf = (ref: string) => draft.proposedMicroservices.find((p) => p.tempKey === ref)?.name ?? names.get(ref) ?? ref;
  for (const f of draft.proposedConnections) {
    const out: DocConnection = { name: f.name, source: nameOf(f.source), target: nameOf(f.target) };
    const kind = toDocKind(f.kind);
    if (kind) out.kind = kind;
    if (f.endpoint) out.endpoint = f.endpoint;
    const flow = f.flowId ? flowNames.get(f.flowId) : undefined;
    if (flow) out.flow = flow;
    if (f.description) out.description = f.description;
    connections.push(out);
  }
  const result = { formatVersion: 2 } as ConnectionsDocument;
  if (microservices.length > 0) result.microservices = microservices;
  if (doc.flows && doc.flows.length > 0) result.flows = doc.flows;
  result.connections = connections;
  return result;
}

/** Serializes like the backend writer (two-space indent, trailing newline). */
export function documentText(doc: ConnectionsDocument): string {
  return JSON.stringify(doc, null, 2) + '\n';
}
