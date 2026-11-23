import { create } from 'zustand';
import { endpointKeyOf, normalizeLabel } from '../canvas/normalize';
import type {
  ConnectionChange,
  ConnectionKind,
  Orphan,
  ProposedEndpoint,
  ProposedConnection,
  ProposedMicroservice,
  ProposedNote,
  Scenario,
  ScenarioContent,
  ScenarioDocument,
  ScenarioRemovals,
  ScenarioWrite,
  SimpleField,
  XYPosition,
} from '../api/types';

export type FieldDirection = 'IN' | 'OUT';
export type RemovalKind = keyof ScenarioRemovals;

function isSelfOrDescendant(path: string, ancestor: string): boolean {
  return path === ancestor || path.startsWith(ancestor + '.') || path.startsWith(ancestor + '[].');
}

export const PROPOSED_PREFIX = 'proposed:';
export const isProposedRef = (ref: string) => ref.startsWith(PROPOSED_PREFIX);

export const emptyRemovals = (): ScenarioRemovals => ({ connections: [], microservices: [], links: [] });

export const emptyDraft = (): ScenarioContent => ({
  proposedMicroservices: [],
  proposedLinks: [],
  proposedConnections: [],
  addedEndpoints: [],
  addedFields: [],
  removals: emptyRemovals(),
  connectionChanges: [],
  positions: {},
  notes: [],
});

/** Fills in lists a server response may omit, so the store can rely on them. */
export function normalizeDraft(content: Partial<ScenarioContent>): ScenarioContent {
  const e = emptyDraft();
  return {
    proposedMicroservices: content.proposedMicroservices ?? e.proposedMicroservices,
    proposedLinks: content.proposedLinks ?? e.proposedLinks,
    proposedConnections: content.proposedConnections ?? e.proposedConnections,
    addedEndpoints: content.addedEndpoints ?? e.addedEndpoints,
    addedFields: content.addedFields ?? e.addedFields,
    removals: { ...emptyRemovals(), ...(content.removals ?? {}) },
    connectionChanges: content.connectionChanges ?? e.connectionChanges,
    positions: content.positions ?? e.positions,
    notes: content.notes ?? [],
  };
}

export interface ConnectionChangeInput {
  name?: string;
  kind?: ConnectionKind | 'NONE';
  description?: string | null;
  /** a flow id, or `NONE` to ungroup (FR-053) */
  flowId?: string;
  /** `METHOD /path`, or `NONE` to clear (FR-056) */
  endpoint?: string;
  /** a proposed step in its flow (spec 004 FR-007); undefined = unchanged */
  step?: number;
}

/**
 * Draft Scenario Mode, content v2 (research R9). The draft lives only in memory until Save; nothing is
 * written to localStorage (constitution: single source of truth). It never touches the baseline.
 */
export interface ScenarioState {
  isDraftMode: boolean;
  scenarioId: string | null;
  name: string;
  description: string | null;
  version: number | null;
  draft: ScenarioContent;
  dirty: boolean;
  orphans: Orphan[];
  baselineDrift: string[];

  enterDraft: () => void;
  exitDraft: () => void;
  reset: () => void;
  setName: (name: string) => void;
  addProposedMicroservice: (name: string, description?: string | null, card?: { title: string; endpoints: ProposedEndpoint[] }) => string;
  removeProposedMicroservice: (tempKey: string) => void;
  /** Renames or re-describes a proposed microservice (draft only; baseline ones are edited outside drafts). */
  updateProposedMicroservice: (tempKey: string, patch: { name?: string; description?: string | null; team?: string | null }) => void;
  /** Adds an endpoint to a baseline card (by card id) or to a proposed microservice's card (by tempKey). */
  addEndpoint: (prefix: string, endpoint: ProposedEndpoint) => void;
  removeAddedEndpoint: (index: number) => void;
  removeProposedCardEndpoint: (tempKey: string, index: number) => void;
  /** Adds a proposed field to any endpoint: baseline, added, or on a proposed card. `prefix` is the endpoint key prefix. */
  addField: (prefix: string, endpointKey: string, direction: FieldDirection, field: SimpleField) => void;
  removeField: (prefix: string, endpointKey: string, direction: FieldDirection, name: string) => void;
  addProposedConnection: (connection: Omit<ProposedConnection, 'id'>) => string | null;
  updateProposedConnection: (id: string, values: Partial<Omit<ProposedConnection, 'id' | 'source' | 'target'>>) => void;
  removeProposedConnection: (id: string) => void;
  addProposedLink: (microservice: string, cardId: string) => void;
  removeProposedLink: (cardId: string) => void;
  toggleRemoval: (kind: RemovalKind, id: string) => void;
  setConnectionChange: (connectionId: string, change: ConnectionChangeInput) => void;
  clearConnectionChange: (connectionId: string) => void;
  setPosition: (nodeKey: string, position: XYPosition) => void;
  /** notes added in the draft (spec 004 FR-020); returns the tempKey */
  addNote: (note: { text: string; kind: ProposedNote['kind']; position: XYPosition }) => string;
  updateNote: (tempKey: string, patch: Partial<Pick<ProposedNote, 'text' | 'kind' | 'position'>>) => void;
  removeNote: (tempKey: string) => void;
  load: (scenario: Scenario) => void;
  markSaved: (scenario: Scenario) => void;
  toScenarioWrite: () => ScenarioWrite & { version?: number };
  toScenarioDocument: () => ScenarioDocument;
}

function slug(name: string): string {
  const s = name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
  return s === '' ? 'service' : s;
}

let connectionCounter = 0;

const fieldsKey = (direction: FieldDirection) => (direction === 'IN' ? 'inputs' : 'outputs');

function editEndpoint(ep: ProposedEndpoint, direction: FieldDirection, edit: (l: SimpleField[]) => SimpleField[]) {
  const key = fieldsKey(direction);
  return { ...ep, [key]: edit(ep[key] ?? []) };
}

/**
 * Applies a field edit where the endpoint lives: on a proposed card's endpoint, on an added endpoint, or (for
 * baseline endpoints) in `addedFields`. `added` is the field being added, if any.
 */
function editFields(
  draft: ScenarioContent,
  prefix: string,
  endpointKey: string,
  direction: FieldDirection,
  edit: (list: SimpleField[]) => SimpleField[],
  added?: SimpleField,
): ScenarioContent {
  if (isProposedRef(prefix)) {
    return {
      ...draft,
      proposedMicroservices: draft.proposedMicroservices.map((p): ProposedMicroservice =>
        p.tempKey !== prefix || !p.card
          ? p
          : {
              ...p,
              card: {
                ...p.card,
                endpoints: p.card.endpoints.map((ep) =>
                  endpointKeyOf(p.tempKey, ep.method, ep.path) === endpointKey ? editEndpoint(ep, direction, edit) : ep,
                ),
              },
            },
      ),
    };
  }
  const addedIndex = draft.addedEndpoints.findIndex(
    (a) => a.cardId === prefix && endpointKeyOf(prefix, a.endpoint.method, a.endpoint.path) === endpointKey,
  );
  if (addedIndex >= 0) {
    return {
      ...draft,
      addedEndpoints: draft.addedEndpoints.map((a, i) =>
        i === addedIndex ? { ...a, endpoint: editEndpoint(a.endpoint, direction, edit) } : a,
      ),
    };
  }
  if (added) {
    return { ...draft, addedFields: [...draft.addedFields, { cardId: prefix, endpointKey, direction, field: added }] };
  }
  const mine = draft.addedFields.filter((f) => f.endpointKey === endpointKey && f.direction === direction).map((f) => f.field);
  const kept = new Set(edit(mine));
  return {
    ...draft,
    addedFields: draft.addedFields.filter((f) => f.endpointKey !== endpointKey || f.direction !== direction || kept.has(f.field)),
  };
}

const toggle = (list: readonly string[], id: string) => (list.includes(id) ? list.filter((x) => x !== id) : [...list, id]);

export const useScenarioStore = create<ScenarioState>()((set, get) => ({
  isDraftMode: false,
  scenarioId: null,
  name: 'Untitled scenario',
  description: null,
  version: null,
  draft: emptyDraft(),
  dirty: false,
  orphans: [],
  baselineDrift: [],

  enterDraft: () => set({ isDraftMode: true }),
  exitDraft: () => set({ isDraftMode: false }),
  reset: () =>
    set({
      scenarioId: null,
      name: 'Untitled scenario',
      description: null,
      version: null,
      draft: emptyDraft(),
      dirty: false,
      orphans: [],
      baselineDrift: [],
    }),
  setName: (name) => set({ name, dirty: true }),

  addProposedMicroservice: (name, description = null, card) => {
    const taken = new Set(get().draft.proposedMicroservices.map((s) => s.tempKey));
    const base = PROPOSED_PREFIX + slug(name);
    let key = base;
    for (let i = 2; taken.has(key); i++) key = `${base}-${i}`;
    const proposed: ProposedMicroservice = { tempKey: key, name: name.trim() || key, description, card: card ?? null };
    set((s) => ({
      draft: { ...s.draft, proposedMicroservices: [...s.draft.proposedMicroservices, proposed] },
      dirty: true,
    }));
    return key;
  },

  removeProposedMicroservice: (tempKey) =>
    set((s) => {
      const positions = { ...s.draft.positions };
      delete positions[tempKey];
      return {
        draft: {
          ...s.draft,
          proposedMicroservices: s.draft.proposedMicroservices.filter((p) => p.tempKey !== tempKey),
          // cascade: proposed connections and links touching the removed microservice go too
          proposedConnections: s.draft.proposedConnections.filter((f) => f.source !== tempKey && f.target !== tempKey),
          proposedLinks: s.draft.proposedLinks.filter((l) => l.microservice !== tempKey),
          positions,
        },
        dirty: true,
      };
    }),

  updateProposedMicroservice: (tempKey, patch) =>
    set((s) => ({
      draft: {
        ...s.draft,
        proposedMicroservices: s.draft.proposedMicroservices.map((p) =>
          p.tempKey !== tempKey
            ? p
            : {
                ...p,
                ...(patch.name !== undefined ? { name: patch.name.trim() || p.name } : {}),
                ...(patch.description !== undefined ? { description: patch.description } : {}),
                ...(patch.team !== undefined ? { team: patch.team?.trim() || null } : {}),
              },
        ),
      },
      dirty: true,
    })),

  addEndpoint: (prefix, endpoint) =>
    set((s) => {
      if (isProposedRef(prefix)) {
        return {
          draft: {
            ...s.draft,
            proposedMicroservices: s.draft.proposedMicroservices.map((p) =>
              p.tempKey === prefix
                ? { ...p, card: { title: p.card?.title ?? p.name, endpoints: [...(p.card?.endpoints ?? []), endpoint] } }
                : p,
            ),
          },
          dirty: true,
        };
      }
      return {
        draft: { ...s.draft, addedEndpoints: [...s.draft.addedEndpoints, { cardId: prefix, endpoint }] },
        dirty: true,
      };
    }),

  removeAddedEndpoint: (index) =>
    set((s) => ({
      draft: { ...s.draft, addedEndpoints: s.draft.addedEndpoints.filter((_, i) => i !== index) },
      dirty: true,
    })),

  removeProposedCardEndpoint: (tempKey, index) =>
    set((s) => ({
      draft: {
        ...s.draft,
        proposedMicroservices: s.draft.proposedMicroservices.map((p) =>
          p.tempKey === tempKey && p.card ? { ...p, card: { ...p.card, endpoints: p.card.endpoints.filter((_, i) => i !== index) } } : p,
        ),
      },
      dirty: true,
    })),

  addField: (prefix, endpointKey, direction, field) =>
    set((s) => ({ draft: editFields(s.draft, prefix, endpointKey, direction, (list) => [...list, field], field), dirty: true })),

  removeField: (prefix, endpointKey, direction, name) =>
    set((s) => ({
      // removing an object field also removes the fields proposed inside it
      draft: editFields(s.draft, prefix, endpointKey, direction, (list) => list.filter((f) => !isSelfOrDescendant(f.name, name))),
      dirty: true,
    })),

  addProposedConnection: (connection) => {
    if (connection.source === connection.target || connection.name.trim() === '') return null;
    const key = (f: Pick<ProposedConnection, 'source' | 'target' | 'name'>) => `${f.source}|${f.target}|${normalizeLabel(f.name)}`;
    if (get().draft.proposedConnections.some((f) => key(f) === key(connection))) return null;
    const existing = new Set(get().draft.proposedConnections.map((e) => e.id));
    let id: string;
    do {
      connectionCounter += 1;
      id = `pf-${connectionCounter}`;
    } while (existing.has(id));
    const created: ProposedConnection = { ...connection, id, name: connection.name.trim() };
    set((s) => ({ draft: { ...s.draft, proposedConnections: [...s.draft.proposedConnections, created] }, dirty: true }));
    return id;
  },

  updateProposedConnection: (id, values) =>
    set((s) => ({
      draft: { ...s.draft, proposedConnections: s.draft.proposedConnections.map((f) => (f.id === id ? { ...f, ...values } : f)) },
      dirty: true,
    })),

  removeProposedConnection: (id) =>
    set((s) => ({
      draft: { ...s.draft, proposedConnections: s.draft.proposedConnections.filter((e) => e.id !== id) },
      dirty: true,
    })),

  addProposedLink: (microservice, cardId) =>
    set((s) => ({
      draft: {
        ...s.draft,
        proposedLinks: [
          ...s.draft.proposedLinks.filter((l) => l.cardId !== cardId && l.microservice !== microservice),
          { microservice, cardId },
        ],
      },
      dirty: true,
    })),

  removeProposedLink: (cardId) =>
    set((s) => ({
      draft: { ...s.draft, proposedLinks: s.draft.proposedLinks.filter((l) => l.cardId !== cardId) },
      dirty: true,
    })),

  toggleRemoval: (kind, id) =>
    set((s) => ({
      draft: { ...s.draft, removals: { ...s.draft.removals, [kind]: toggle(s.draft.removals[kind], id) } },
      dirty: true,
    })),

  setConnectionChange: (connectionId, change) =>
    set((s) => {
      const others = s.draft.connectionChanges.filter((c) => c.connectionId !== connectionId);
      const current = s.draft.connectionChanges.find((c) => c.connectionId === connectionId);
      const next: ConnectionChange = { ...(current ?? { connectionId }), ...change, connectionId };
      return { draft: { ...s.draft, connectionChanges: [...others, next] }, dirty: true };
    }),

  clearConnectionChange: (connectionId) =>
    set((s) => ({
      draft: { ...s.draft, connectionChanges: s.draft.connectionChanges.filter((c) => c.connectionId !== connectionId) },
      dirty: true,
    })),

  addNote: (note) => {
    const used = new Set((get().draft.notes ?? []).map((n) => n.tempKey));
    let i = used.size + 1;
    while (used.has(`proposed-note:${i}`)) i++;
    const tempKey = `proposed-note:${i}`;
    set((s) => ({ draft: { ...s.draft, notes: [...(s.draft.notes ?? []), { tempKey, ...note }] }, dirty: true }));
    return tempKey;
  },
  updateNote: (tempKey, patch) =>
    set((s) => ({
      draft: { ...s.draft, notes: (s.draft.notes ?? []).map((n) => (n.tempKey === tempKey ? { ...n, ...patch } : n)) },
      dirty: true,
    })),
  removeNote: (tempKey) =>
    set((s) => ({ draft: { ...s.draft, notes: (s.draft.notes ?? []).filter((n) => n.tempKey !== tempKey) }, dirty: true })),

  setPosition: (nodeKey, position) =>
    set((s) => ({ draft: { ...s.draft, positions: { ...s.draft.positions, [nodeKey]: position } }, dirty: true })),

  load: (scenario) =>
    set({
      scenarioId: scenario.id,
      name: scenario.name,
      description: scenario.description ?? null,
      version: scenario.version,
      draft: normalizeDraft(scenario.content),
      dirty: false,
      orphans: scenario.orphans,
      baselineDrift: scenario.baselineDrift,
      isDraftMode: true,
    }),

  markSaved: (scenario) =>
    set({
      scenarioId: scenario.id,
      name: scenario.name,
      version: scenario.version,
      dirty: false,
      orphans: scenario.orphans,
      baselineDrift: scenario.baselineDrift,
    }),

  toScenarioWrite: () => {
    const s = get();
    const write: ScenarioWrite & { version?: number } = {
      name: s.name.trim(),
      description: s.description,
      content: s.draft,
    };
    if (s.version !== null) write.version = s.version;
    return write;
  },

  toScenarioDocument: () => {
    const s = get();
    return {
      formatVersion: 3,
      name: s.name.trim(),
      description: s.description,
      exportedAt: new Date().toISOString(),
      content: s.draft,
    };
  },
}));
