import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { request } from './client';
import * as edits from '../history/baselineEdits';
import type {
  CanvasResponse,
  ConnectionCreate,
  Flow,
  ConnectionKind,
  ConnectionMutation,
  ConnectionsDocumentApplied,
  ConnectionsDocumentExport,
  ConnectionsDocumentPreview,
  ImportCardRequest,
  LayoutUpdate,
  MicroserviceMutation,
  Note,
  NoteKind,
  XYPosition,
  Scenario,
  ScenarioDocument,
  ScenarioSummary,
  ScenarioWrite,
  SpecCardSummary,
} from './types';

export const keys = {
  canvas: ['canvas'] as const,
  cards: ['cards'] as const,
  connectionsDocument: ['connections-document'] as const,
  scenarios: ['scenarios'] as const,
  scenario: (id: string) => ['scenarios', id] as const,
};

// ---------------------------------------------------------------- canvas

export function useCanvas() {
  return useQuery({ queryKey: keys.canvas, queryFn: () => request<CanvasResponse>('GET', '/canvas') });
}

/** Canvas, cards and the connections document all derive from the baseline: refresh them together. */
export function useInvalidateBaseline() {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: keys.canvas });
    void qc.invalidateQueries({ queryKey: keys.cards });
    void qc.invalidateQueries({ queryKey: keys.connectionsDocument });
  };
}

export function useSaveLayout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: LayoutUpdate) => request<{ version: number }>('PUT', '/layout', { body }),
    onSettled: () => void qc.invalidateQueries({ queryKey: keys.canvas }),
  });
}

// ---------------------------------------------------------------- cards (US1)

export function useCards() {
  return useQuery({ queryKey: keys.cards, queryFn: () => request<SpecCardSummary[]>('GET', '/cards') });
}

export function useImportCard() {
  const invalidate = useInvalidateBaseline();
  return useMutation({
    mutationFn: (body: ImportCardRequest) => request<SpecCardSummary>('POST', '/cards', { body }),
    onSuccess: invalidate,
  });
}

export function useSyncCard() {
  const invalidate = useInvalidateBaseline();
  return useMutation({
    mutationFn: ({ cardId, content }: { cardId: string; content?: string }) =>
      request<SpecCardSummary>('POST', '/cards/{cardId}/sync', {
        params: { cardId },
        body: content === undefined ? undefined : { content },
      }),
    // a failed sync is stored too ("sync failed" badge), so refresh either way
    onSettled: invalidate,
  });
}

export function useDeleteCard() {
  const invalidate = useInvalidateBaseline();
  return useMutation({
    mutationFn: (cardId: string) => request<null>('DELETE', '/cards/{cardId}', { params: { cardId } }),
    onSuccess: invalidate,
  });
}

// ---------------------------------------------------------------- microservices (US2)

/** Every baseline edit below is recorded for undo/redo (spec 004 FR-008, history/baselineEdits.ts). */
export function useCreateMicroservice() {
  const invalidate = useInvalidateBaseline();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: edits.MicroserviceBody) => request<MicroserviceMutation>('POST', '/microservices', { body }),
    onSuccess: (created, body) => {
      invalidate();
      edits.recordCreateMicroservice(qc, body, created);
    },
  });
}

export function useUpdateMicroservice() {
  const invalidate = useInvalidateBaseline();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, patch }: { id: string; patch: Partial<edits.MicroserviceBody> }) =>
      request<MicroserviceMutation>('PATCH', '/microservices/{microserviceId}', {
        params: { microserviceId: id },
        body: patch,
      }),
    onMutate: () => edits.snapshot(qc),
    onSuccess: (_r, { id, patch }, snap) => {
      invalidate();
      edits.recordUpdateMicroservice(qc, snap, id, patch);
    },
  });
}

export function useDeleteMicroservice() {
  const invalidate = useInvalidateBaseline();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) =>
      request<{ danglingConnectionIds: string[]; revision: number }>('DELETE', '/microservices/{microserviceId}', {
        params: { microserviceId: id },
      }),
    onMutate: () => edits.snapshot(qc),
    onSuccess: (_r, id, snap) => {
      invalidate();
      edits.recordDeleteMicroservice(qc, snap, id);
    },
  });
}

export function useLinkCard() {
  const invalidate = useInvalidateBaseline();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ microserviceId, cardId, replace }: { microserviceId: string; cardId: string; replace?: boolean }) =>
      request<null>('PUT', '/microservices/{microserviceId}/card', {
        params: { microserviceId },
        body: { cardId, replace: replace ?? false },
      }),
    onMutate: () => edits.snapshot(qc),
    onSuccess: (_r, { microserviceId, cardId }, snap) => {
      invalidate();
      edits.recordLink(qc, snap, microserviceId, cardId);
    },
  });
}

export function useUnlinkCard() {
  const invalidate = useInvalidateBaseline();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (microserviceId: string) =>
      request<null>('DELETE', '/microservices/{microserviceId}/card', { params: { microserviceId } }),
    onMutate: () => edits.snapshot(qc),
    onSuccess: (_r, microserviceId, snap) => {
      invalidate();
      edits.recordUnlink(qc, snap, microserviceId);
    },
  });
}

// ---------------------------------------------------------------- connections (US3)

export function useCreateConnection() {
  const invalidate = useInvalidateBaseline();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ConnectionCreate) => request<ConnectionMutation>('POST', '/connections', { body }),
    onSuccess: (created, body) => {
      invalidate();
      edits.recordCreateConnection(qc, body as edits.ConnectionBody, created);
    },
  });
}

export function useUpdateConnection() {
  const invalidate = useInvalidateBaseline();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({
      id,
      patch,
    }: {
      id: string;
      patch: {
        name?: string;
        kind?: ConnectionKind | null;
        description?: string | null;
        flowId?: string | null;
        endpoint?: string | null;
        step?: number | null;
      };
    }) => request<ConnectionMutation>('PATCH', '/connections/{connectionId}', { params: { connectionId: id }, body: patch }),
    onMutate: () => edits.snapshot(qc),
    onSuccess: (_r, { id, patch }, snap) => {
      invalidate();
      edits.recordUpdateConnection(qc, snap, id, patch);
    },
  });
}

export function useDeleteConnection() {
  const invalidate = useInvalidateBaseline();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => request<null>('DELETE', '/connections/{connectionId}', { params: { connectionId: id } }),
    onMutate: () => edits.snapshot(qc),
    onSuccess: (_r, id, snap) => {
      invalidate();
      edits.recordDeleteConnection(qc, snap, id);
    },
  });
}

export function useReassignConnection() {
  const invalidate = useInvalidateBaseline();
  return useMutation({
    mutationFn: ({ id, sourceId, targetId }: { id: string; sourceId?: string; targetId?: string }) =>
      request<ConnectionMutation>('POST', '/connections/{connectionId}/reassign', { params: { connectionId: id }, body: { sourceId, targetId } }),
    onSuccess: invalidate,
  });
}

// ---------------------------------------------------------------- flows (FR-049)

export function useCreateFlow() {
  const invalidate = useInvalidateBaseline();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { name: string; color: Flow['color'] }) =>
      request<{ flow: Flow; revision: number }>('POST', '/flows', { body }),
    onSuccess: (created, body) => {
      invalidate();
      edits.recordCreateFlow(qc, body, created);
    },
  });
}

export function useUpdateFlow() {
  const invalidate = useInvalidateBaseline();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, patch }: { id: string; patch: { name?: string; color?: Flow['color'] } }) =>
      request<{ flow: Flow; revision: number }>('PATCH', '/flows/{flowId}', { params: { flowId: id }, body: patch }),
    onMutate: () => edits.snapshot(qc),
    onSuccess: (_r, { id, patch }, snap) => {
      invalidate();
      edits.recordUpdateFlow(qc, snap, id, patch);
    },
  });
}

/** Sets every step of a flow at once (spec 004 FR-004). */
export function useSetFlowSteps() {
  const invalidate = useInvalidateBaseline();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ flowId, steps }: { flowId: string; steps: { connectionId: string; step: number }[] }) =>
      request<{ revision: number }>('PUT', '/flows/{flowId}/steps', { params: { flowId }, body: { steps } }),
    onMutate: () => edits.snapshot(qc),
    onSuccess: (_r, { flowId, steps }, snap) => {
      invalidate();
      edits.recordSetSteps(qc, snap, flowId, steps);
    },
  });
}

export function useDeleteFlow() {
  const invalidate = useInvalidateBaseline();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => request<null>('DELETE', '/flows/{flowId}', { params: { flowId: id } }),
    onMutate: () => edits.snapshot(qc),
    onSuccess: (_r, id, snap) => {
      invalidate();
      edits.recordDeleteFlow(qc, snap, id);
    },
  });
}

// ---------------------------------------------------------------- connections document (US3)

export function fetchConnectionsDocument() {
  return request<ConnectionsDocumentExport>('GET', '/connections-document');
}

export function useConnectionsDocument(enabled = true) {
  return useQuery({ queryKey: keys.connectionsDocument, queryFn: fetchConnectionsDocument, enabled });
}

export function previewConnectionsDocument(text: string) {
  return request<ConnectionsDocumentPreview>('POST', '/connections-document/preview', { body: { text } });
}

export function useApplyConnectionsDocument() {
  const invalidate = useInvalidateBaseline();
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ text, baseRevision }: { text: string; baseRevision: number | null }) => {
      // the document before, so the apply can be undone (spec 004 research R3)
      const before = await fetchConnectionsDocument();
      const applied = await request<ConnectionsDocumentApplied>('PUT', '/connections-document', { body: { text, baseRevision } });
      return { applied, before: before.text };
    },
    onSuccess: ({ applied, before }, { text }) => {
      invalidate();
      if (!applied.changes.unchanged) edits.recordApplyDocument(qc, before, text, applied.changes.microservicesAdded);
    },
  });
}

// ---------------------------------------------------------------- notes (spec 004 US4)

export function useCreateNote() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { text: string; kind: NoteKind; position: XYPosition }) => request<Note>('POST', '/notes', { body }),
    onSuccess: (created) => {
      void qc.invalidateQueries({ queryKey: keys.canvas });
      edits.recordCreateNote(qc, created);
    },
  });
}

export function useUpdateNote() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, patch }: { id: string; patch: Partial<edits.NoteBody> }) =>
      request<Note>('PATCH', '/notes/{noteId}', { params: { noteId: id }, body: patch }),
    onMutate: () => edits.snapshot(qc),
    onSuccess: (_r, { id, patch }, snap) => {
      void qc.invalidateQueries({ queryKey: keys.canvas });
      edits.recordUpdateNote(qc, snap, id, patch);
    },
  });
}

export function useDeleteNote() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => request<null>('DELETE', '/notes/{noteId}', { params: { noteId: id } }),
    onMutate: () => edits.snapshot(qc),
    onSuccess: (_r, id, snap) => {
      void qc.invalidateQueries({ queryKey: keys.canvas });
      edits.recordDeleteNote(qc, snap, id);
    },
  });
}

// ---------------------------------------------------------------- scenarios (US5)

export function useScenarios() {
  return useQuery({ queryKey: keys.scenarios, queryFn: () => request<ScenarioSummary[]>('GET', '/scenarios') });
}

function useInvalidateScenarios() {
  const qc = useQueryClient();
  return () => void qc.invalidateQueries({ queryKey: keys.scenarios });
}

export function useCreateScenario() {
  const invalidate = useInvalidateScenarios();
  return useMutation({
    mutationFn: (body: ScenarioWrite) => request<Scenario>('POST', '/scenarios', { body }),
    onSuccess: invalidate,
  });
}

export function useUpdateScenario() {
  const invalidate = useInvalidateScenarios();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: ScenarioWrite & { version: number } }) =>
      request<Scenario>('PUT', '/scenarios/{scenarioId}', { params: { scenarioId: id }, body }),
    onSuccess: invalidate,
  });
}

export function useDeleteScenario() {
  const invalidate = useInvalidateScenarios();
  return useMutation({
    mutationFn: (id: string) => request<null>('DELETE', '/scenarios/{scenarioId}', { params: { scenarioId: id } }),
    onSuccess: invalidate,
  });
}

export function useImportScenario() {
  const invalidate = useInvalidateScenarios();
  return useMutation({
    mutationFn: (doc: unknown) => request<Scenario>('POST', '/scenarios/import', { body: doc }),
    onSuccess: invalidate,
  });
}

export function fetchScenario(id: string) {
  return request<Scenario>('GET', '/scenarios/{scenarioId}', { params: { scenarioId: id } });
}

export function fetchScenarioExport(id: string) {
  return request<ScenarioDocument>('GET', '/scenarios/{scenarioId}/export', { params: { scenarioId: id } });
}
