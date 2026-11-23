import { beforeEach, describe, expect, it, vi } from 'vitest';
import { HISTORY_LIMIT, recordEdit, remapId, resolveId, useHistoryStore } from '../../src/history/historyStore';
import { installDraftHistory } from '../../src/history/draftHistory';
import { emptyDraft, useScenarioStore } from '../../src/scenario/scenarioStore';

const h = () => useHistoryStore.getState();

describe('undo/redo history (spec 004 FR-008, FR-012)', () => {
  beforeEach(() => {
    h().reset();
    useHistoryStore.setState({ running: false, onError: () => undefined });
  });

  it('undoes and redoes in order, and a new edit clears redo', async () => {
    const log: string[] = [];
    const entry = (n: string) => ({ label: n, undo: () => void log.push(`undo ${n}`), redo: () => void log.push(`redo ${n}`) });
    recordEdit(entry('a'));
    recordEdit(entry('b'));
    await h().undo();
    await h().undo();
    await h().redo();
    expect(log).toEqual(['undo b', 'undo a', 'redo a']);
    expect(h().future.map((e) => e.label)).toEqual(['b']);
    recordEdit(entry('c'));
    expect(h().future).toEqual([]);
    expect(h().past.map((e) => e.label)).toEqual(['a', 'c']);
  });

  it(`keeps the last ${HISTORY_LIMIT} edits`, () => {
    for (let i = 0; i < HISTORY_LIMIT + 5; i++) recordEdit({ label: String(i), undo: () => undefined, redo: () => undefined });
    expect(h().past).toHaveLength(HISTORY_LIMIT);
    expect(h().past[0]!.label).toBe('5');
  });

  it('reports a failed undo and keeps the entry where it was', async () => {
    const onError = vi.fn();
    useHistoryStore.setState({ onError });
    recordEdit({ label: 'Rename', undo: () => Promise.reject(new Error('name taken')), redo: () => undefined });
    await h().undo();
    expect(onError).toHaveBeenCalledWith('Could not undo "Rename": name taken');
    expect(h().past.map((e) => e.label)).toEqual(['Rename']);
    expect(h().future).toEqual([]);
  });

  it('does not record edits made by an undo', async () => {
    recordEdit({ label: 'a', undo: () => recordEdit({ label: 'nested', undo: () => undefined, redo: () => undefined }), redo: () => undefined });
    await h().undo();
    expect(h().past).toEqual([]);
    expect(h().future.map((e) => e.label)).toEqual(['a']);
  });

  it('follows ids of objects recreated more than once (FR-009)', () => {
    remapId('ms-1', 'ms-2');
    remapId('ms-1', 'ms-3');
    expect(resolveId('ms-1')).toBe('ms-3');
    expect(resolveId('ms-2')).toBe('ms-3');
    expect(resolveId('other')).toBe('other');
  });
});

describe('draft history (FR-010)', () => {
  it('records draft edits, undoes them on the draft only, and starts fresh when the mode changes', async () => {
    useScenarioStore.setState({ isDraftMode: false, draft: emptyDraft(), scenarioId: null });
    const off = installDraftHistory();
    try {
      useScenarioStore.getState().enterDraft();
      h().reset();
      useScenarioStore.getState().addProposedMicroservice('Loyalty', null);
      expect(h().past.map((e) => e.label)).toEqual(['Propose microservice']);
      await h().undo();
      expect(useScenarioStore.getState().draft.proposedMicroservices).toEqual([]);
      await h().redo();
      expect(useScenarioStore.getState().draft.proposedMicroservices.map((m) => m.name)).toEqual(['Loyalty']);
      useScenarioStore.getState().exitDraft();
      expect(h().past).toEqual([]);
    } finally {
      off();
    }
  });
});
