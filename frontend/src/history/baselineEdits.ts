import type { QueryClient } from '@tanstack/react-query';
import { request } from '../api/client';
import type { CanvasResponse, ConnectionMutation, Flow, MicroserviceMutation, MicroserviceNodeData, Note, NoteKind, XYPosition } from '../api/types';
import { useCanvasStore } from '../canvas/canvasStore';
import { useScenarioStore } from '../scenario/scenarioStore';
import { recordEdit, remapId, resolveId } from './historyStore';

/**
 * Undo/redo of baseline edits (spec 004 FR-008, FR-009, research R3). Each function records one entry whose
 * undo/redo call the API directly (so they are not recorded again) and refresh the baseline queries. Ids of
 * recreated objects are remapped, and every entry resolves ids when it runs. Nothing is recorded in Draft
 * Scenario Mode: there the draft has its own history (FR-010).
 */

const CANVAS = ['canvas'] as const;

async function refresh(qc: QueryClient) {
  await Promise.all([
    qc.invalidateQueries({ queryKey: CANVAS }),
    qc.invalidateQueries({ queryKey: ['cards'] }),
    qc.invalidateQueries({ queryKey: ['connections-document'] }),
  ]);
}

export const snapshot = (qc: QueryClient) => qc.getQueryData<CanvasResponse>(CANVAS);

function record(qc: QueryClient, label: string, undo: () => Promise<void>, redo: () => Promise<void>) {
  if (useScenarioStore.getState().isDraftMode) return;
  recordEdit({
    label,
    undo: async () => {
      try {
        await undo();
      } finally {
        await refresh(qc);
      }
    },
    redo: async () => {
      try {
        await redo();
      } finally {
        await refresh(qc);
      }
    },
  });
}

const msData = (snap: CanvasResponse | undefined, id: string) => {
  const n = snap?.nodes.find((x) => x.id === `ms:${id}`);
  return n ? { data: n.data as MicroserviceNodeData, position: n.position ?? null } : null;
};
const edgeData = (snap: CanvasResponse | undefined, id: string) => snap?.edges.find((e) => e.data.connectionId === id);
const nameOf = (snap: CanvasResponse | undefined, id: string) => msData(snap, id)?.data.name ?? 'microservice';

// ---------------------------------------------------------------- microservices

export interface MicroserviceBody {
  name: string;
  description?: string | null;
  team?: string | null;
  repositoryUrl?: string | null;
  docsUrl?: string | null;
}

const createMs = (body: MicroserviceBody) => request<MicroserviceMutation>('POST', '/microservices', { body });
const deleteMs = (id: string) => request<null>('DELETE', '/microservices/{microserviceId}', { params: { microserviceId: resolveId(id) } });
const patchMs = (id: string, body: Partial<MicroserviceBody>) =>
  request<MicroserviceMutation>('PATCH', '/microservices/{microserviceId}', { params: { microserviceId: resolveId(id) }, body });
const linkMs = (id: string, cardId: string, replace = true) =>
  request<null>('PUT', '/microservices/{microserviceId}/card', { params: { microserviceId: resolveId(id) }, body: { cardId, replace } });
const unlinkMs = (id: string) => request<null>('DELETE', '/microservices/{microserviceId}/card', { params: { microserviceId: resolveId(id) } });

export function recordCreateMicroservice(qc: QueryClient, body: MicroserviceBody, created: MicroserviceMutation) {
  const id = created.microservice.id;
  record(
    qc,
    `Create ${body.name}`,
    async () => void (await deleteMs(id)),
    async () => remapId(id, (await createMs(body)).microservice.id),
  );
}

export function recordUpdateMicroservice(qc: QueryClient, snap: CanvasResponse | undefined, id: string, patch: Partial<MicroserviceBody>) {
  const before = msData(snap, id)?.data;
  if (!before) return;
  const previous: Partial<MicroserviceBody> = {};
  for (const key of Object.keys(patch) as (keyof MicroserviceBody)[]) {
    (previous as Record<string, unknown>)[key] = (before as unknown as Record<string, unknown>)[key] ?? null;
  }
  record(
    qc,
    patch.name && patch.name !== before.name ? `Rename ${before.name}` : `Edit ${before.name}`,
    async () => void (await patchMs(id, previous)),
    async () => void (await patchMs(id, patch)),
  );
}

/** Undo recreates the microservice with its name, description, ownership, position, card and collapse (FR-009). */
export function recordDeleteMicroservice(qc: QueryClient, snap: CanvasResponse | undefined, id: string) {
  const m = msData(snap, id);
  if (!m) return;
  const d = m.data;
  const body: MicroserviceBody = { name: d.name, description: d.description ?? null, team: d.team ?? null, repositoryUrl: d.repositoryUrl ?? null, docsUrl: d.docsUrl ?? null };
  record(
    qc,
    `Delete ${d.name}`,
    async () => {
      // connections left dangling by the delete re-attach by name on create (003 research R2)
      const created = await createMs(body);
      const newId = created.microservice.id;
      remapId(id, newId);
      if (d.specCardId) await linkMs(newId, d.specCardId, false);
      await qc.refetchQueries({ queryKey: CANVAS });
      useCanvasStore.getState().actions.applyLayout({
        positions: m.position ? { [`ms:${newId}`]: m.position } : {},
        collapse: d.specCardId ? { [d.specCardId]: d.cardCollapsed } : {},
      });
    },
    async () => void (await deleteMs(id)),
  );
}

export function recordLink(qc: QueryClient, snap: CanvasResponse | undefined, microserviceId: string, cardId: string) {
  const previous = msData(snap, microserviceId)?.data.specCardId ?? null;
  record(
    qc,
    `Link spec to ${nameOf(snap, microserviceId)}`,
    async () => void (await (previous ? linkMs(microserviceId, previous) : unlinkMs(microserviceId))),
    async () => void (await linkMs(microserviceId, cardId)),
  );
}

export function recordUnlink(qc: QueryClient, snap: CanvasResponse | undefined, microserviceId: string) {
  const previous = msData(snap, microserviceId)?.data.specCardId ?? null;
  if (!previous) return;
  record(
    qc,
    `Unlink spec from ${nameOf(snap, microserviceId)}`,
    async () => void (await linkMs(microserviceId, previous, false)),
    async () => void (await unlinkMs(microserviceId)),
  );
}

// ---------------------------------------------------------------- connections

export interface ConnectionBody {
  sourceId: string;
  targetId: string;
  name: string;
  kind?: 'REQUEST' | 'EVENT' | null;
  description?: string | null;
  flowId?: string | null;
  endpoint?: string | null;
  step?: number | null;
}

const createConn = (b: ConnectionBody) =>
  request<ConnectionMutation>('POST', '/connections', {
    body: { ...b, sourceId: resolveId(b.sourceId), targetId: resolveId(b.targetId), flowId: b.flowId ? resolveId(b.flowId) : (b.flowId ?? null) },
  });
const deleteConn = (id: string) => request<null>('DELETE', '/connections/{connectionId}', { params: { connectionId: resolveId(id) } });
const patchConn = (id: string, body: Record<string, unknown>) => {
  const flowId = body.flowId;
  return request<ConnectionMutation>('PATCH', '/connections/{connectionId}', {
    params: { connectionId: resolveId(id) },
    body: typeof flowId === 'string' ? { ...body, flowId: resolveId(flowId) } : body,
  });
};

export function recordCreateConnection(qc: QueryClient, body: ConnectionBody, created: ConnectionMutation) {
  const id = created.connection.id;
  record(
    qc,
    `Add connection ${body.name}`,
    async () => void (await deleteConn(id)),
    async () => remapId(id, (await createConn(body)).connection.id),
  );
}

export function recordUpdateConnection(qc: QueryClient, snap: CanvasResponse | undefined, id: string, patch: Record<string, unknown>) {
  const e = edgeData(snap, id);
  if (!e) return;
  const before: Record<string, unknown> = {
    name: e.data.name,
    kind: e.data.kind ?? null,
    description: e.data.description ?? null,
    flowId: e.data.flowId ?? null,
    endpoint: e.data.endpoint ?? null,
  };
  const previous: Record<string, unknown> = {};
  for (const key of Object.keys(patch)) previous[key] = before[key];
  // a flow change moves the step too: restore it (FR-002)
  if ('flowId' in patch || 'step' in patch) previous.step = e.data.flowId ? (e.data.step ?? null) : null;
  if (previous.step === null && previous.flowId) delete previous.step;
  record(
    qc,
    `Edit connection ${e.data.name}`,
    async () => void (await patchConn(id, previous)),
    async () => void (await patchConn(id, patch)),
  );
}

export function recordDeleteConnection(qc: QueryClient, snap: CanvasResponse | undefined, id: string) {
  const e = edgeData(snap, id);
  if (!e) return; // dangling connections can't be recreated with their kept names: not undoable
  const body: ConnectionBody = {
    sourceId: e.source.replace(/^ms:/, ''),
    targetId: e.target.replace(/^ms:/, ''),
    name: e.data.name,
    kind: e.data.kind ?? null,
    description: e.data.description ?? null,
    flowId: e.data.flowId ?? null,
    endpoint: e.data.endpoint ?? null,
    step: e.data.flowId ? (e.data.step ?? null) : null,
  };
  record(
    qc,
    `Delete connection ${e.data.name}`,
    async () => remapId(id, (await createConn(body)).connection.id),
    async () => void (await deleteConn(id)),
  );
}

// ---------------------------------------------------------------- flows and steps

const createFlow = (name: string, color: Flow['color']) => request<{ flow: Flow; revision: number }>('POST', '/flows', { body: { name, color } });
const deleteFlow = (id: string) => request<null>('DELETE', '/flows/{flowId}', { params: { flowId: resolveId(id) } });
const patchFlow = (id: string, body: { name?: string; color?: Flow['color'] }) =>
  request<{ flow: Flow; revision: number }>('PATCH', '/flows/{flowId}', { params: { flowId: resolveId(id) }, body });
const putSteps = (flowId: string, steps: { connectionId: string; step: number }[]) =>
  request<{ revision: number }>('PUT', '/flows/{flowId}/steps', {
    params: { flowId: resolveId(flowId) },
    body: { steps: steps.map((s) => ({ connectionId: resolveId(s.connectionId), step: s.step })) },
  });

export function recordCreateFlow(qc: QueryClient, body: { name: string; color: Flow['color'] }, created: { flow: Flow }) {
  const id = created.flow.id;
  record(
    qc,
    `Create flow ${body.name}`,
    async () => void (await deleteFlow(id)),
    async () => remapId(id, (await createFlow(body.name, body.color)).flow.id),
  );
}

export function recordUpdateFlow(qc: QueryClient, snap: CanvasResponse | undefined, id: string, patch: { name?: string; color?: Flow['color'] }) {
  const flow = snap?.flows.find((f) => f.id === id);
  if (!flow) return;
  const previous: { name?: string; color?: Flow['color'] } = {};
  if (patch.name !== undefined) previous.name = flow.name;
  if (patch.color !== undefined) previous.color = flow.color;
  record(
    qc,
    `Edit flow ${flow.name}`,
    async () => void (await patchFlow(id, previous)),
    async () => void (await patchFlow(id, patch)),
  );
}

/** Undo recreates the flow and puts its connections back in it, at their steps. */
export function recordDeleteFlow(qc: QueryClient, snap: CanvasResponse | undefined, id: string) {
  const flow = snap?.flows.find((f) => f.id === id);
  if (!flow) return;
  const members = (snap?.edges ?? []).filter((e) => e.data.flowId === id).map((e) => ({ id: e.data.connectionId, step: e.data.step ?? null }));
  record(
    qc,
    `Delete flow ${flow.name}`,
    async () => {
      const created = await createFlow(flow.name, flow.color);
      remapId(id, created.flow.id);
      for (const m of members) await patchConn(m.id, m.step === null ? { flowId: created.flow.id } : { flowId: created.flow.id, step: m.step });
    },
    async () => void (await deleteFlow(id)),
  );
}

export function recordSetSteps(qc: QueryClient, snap: CanvasResponse | undefined, flowId: string, steps: { connectionId: string; step: number }[]) {
  const flow = snap?.flows.find((f) => f.id === flowId);
  const members = (snap?.edges ?? []).filter((e) => e.data.flowId === flowId);
  // connections without a step get one after the numbered ones (a step can't be cleared inside a flow)
  let next = Math.max(0, ...members.map((e) => e.data.step ?? 0));
  const previous = members.map((e) => ({ connectionId: e.data.connectionId, step: e.data.step ?? ++next }));
  record(
    qc,
    `Reorder ${flow?.name ?? 'flow'}`,
    async () => void (await putSteps(flowId, previous)),
    async () => void (await putSteps(flowId, steps)),
  );
}

// ---------------------------------------------------------------- connections document

/** Undo re-applies the previous document and deletes the microservices the apply created. */
export function recordApplyDocument(qc: QueryClient, previousText: string, text: string, microservicesAdded: string[]) {
  const apply = (t: string) => request<unknown>('PUT', '/connections-document', { body: { text: t, baseRevision: null } });
  record(
    qc,
    'Apply connections document',
    async () => {
      await apply(previousText);
      const fresh = await request<CanvasResponse>('GET', '/canvas');
      const added = new Set(microservicesAdded.map((n) => n.trim().toLowerCase()));
      for (const n of fresh.nodes) {
        const d = n.data as MicroserviceNodeData;
        if (n.type === 'microservice' && added.has(d.name.trim().toLowerCase())) {
          await request<null>('DELETE', '/microservices/{microserviceId}', { params: { microserviceId: d.microserviceId } });
        }
      }
    },
    async () => void (await apply(text)),
  );
}

// ---------------------------------------------------------------- notes (spec 004 US4)

export interface NoteBody {
  text: string;
  kind: NoteKind;
  position: XYPosition;
}

const createNote = (b: NoteBody) => request<Note>('POST', '/notes', { body: b });
const deleteNote = (id: string) => request<null>('DELETE', '/notes/{noteId}', { params: { noteId: resolveId(id) } });
const patchNote = (id: string, body: Partial<NoteBody>) => request<Note>('PATCH', '/notes/{noteId}', { params: { noteId: resolveId(id) }, body });

export function recordCreateNote(qc: QueryClient, created: Note) {
  const body: NoteBody = { text: created.text, kind: created.kind, position: created.position };
  record(
    qc,
    'Add note',
    async () => void (await deleteNote(created.id)),
    async () => remapId(created.id, (await createNote(body)).id),
  );
}

export function recordUpdateNote(qc: QueryClient, snap: CanvasResponse | undefined, id: string, patch: Partial<NoteBody>) {
  const note = snap?.notes.find((n) => n.id === id);
  if (!note) return;
  const previous: Partial<NoteBody> = {};
  if (patch.text !== undefined) previous.text = note.text;
  if (patch.kind !== undefined) previous.kind = note.kind;
  if (patch.position !== undefined) previous.position = note.position;
  record(
    qc,
    patch.position && Object.keys(patch).length === 1 ? 'Move note' : 'Edit note',
    async () => void (await patchNote(id, previous)),
    async () => void (await patchNote(id, patch)),
  );
}

export function recordDeleteNote(qc: QueryClient, snap: CanvasResponse | undefined, id: string) {
  const note = snap?.notes.find((n) => n.id === id);
  if (!note) return;
  record(
    qc,
    'Delete note',
    async () => remapId(id, (await createNote({ text: note.text, kind: note.kind, position: note.position })).id),
    async () => void (await deleteNote(id)),
  );
}
