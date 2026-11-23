import { create } from 'zustand';

/** One undoable edit (spec 004 FR-008, research R3). `undo` and `redo` re-run against the server or the draft. */
export interface HistoryEntry {
  /** shown on the buttons, e.g. "Delete Orders" */
  label: string;
  undo: () => Promise<void> | void;
  redo: () => Promise<void> | void;
}

export const HISTORY_LIMIT = 50;

interface HistoryState {
  past: HistoryEntry[];
  future: HistoryEntry[];
  /** an undo or redo is running: edits it makes are not recorded */
  running: boolean;
  /** old id → new id, for objects recreated by an undo (FR-009) */
  ids: Record<string, string>;
  record: (entry: HistoryEntry) => void;
  undo: () => Promise<void>;
  redo: () => Promise<void>;
  /** a fresh history, e.g. when entering or leaving Draft Scenario Mode (FR-010) */
  reset: () => void;
  /** callers report failures (a toast); set by the app */
  onError: (message: string) => void;
}

/** Follows the remap chain: an object deleted and recreated twice has been renamed twice. */
export function resolveId(id: string): string {
  const ids = useHistoryStore.getState().ids;
  let current = id;
  for (let i = 0; i < 100 && ids[current]; i++) current = ids[current]!;
  return current;
}

/** Records that `oldId` now lives on as `newId`. */
export function remapId(oldId: string, newId: string) {
  const from = resolveId(oldId);
  if (from === newId) return;
  useHistoryStore.setState((s) => ({ ids: { ...s.ids, [from]: newId } }));
}

const messageOf = (e: unknown) => (e instanceof Error ? e.message : String(e));

/** Undo/redo run one after the other: quick repeated presses are queued instead of dropped. */
let queue: Promise<void> = Promise.resolve();

async function step(direction: 'undo' | 'redo') {
  const { past, future, onError } = useHistoryStore.getState();
  const from = direction === 'undo' ? past : future;
  const entry = from[from.length - 1];
  if (!entry) return;
  useHistoryStore.setState({ running: true });
  try {
    await (direction === 'undo' ? entry.undo() : entry.redo());
    useHistoryStore.setState((s) =>
      direction === 'undo'
        ? { past: s.past.slice(0, -1), future: [...s.future, entry] }
        : { future: s.future.slice(0, -1), past: [...s.past, entry].slice(-HISTORY_LIMIT) },
    );
  } catch (e) {
    // the entry stays where it was, so the history remains consistent (FR-012)
    onError(`Could not ${direction} "${entry.label}": ${messageOf(e)}`);
  } finally {
    useHistoryStore.setState({ running: false });
  }
}

export const useHistoryStore = create<HistoryState>()((set, get) => ({
  past: [],
  future: [],
  running: false,
  ids: {},
  onError: () => undefined,

  record: (entry) => {
    if (get().running) return;
    set((s) => ({ past: [...s.past, entry].slice(-HISTORY_LIMIT), future: [] }));
  },

  undo: () => (queue = queue.then(() => step('undo'))),
  redo: () => (queue = queue.then(() => step('redo'))),

  reset: () => set({ past: [], future: [], ids: {} }),
}));

/** Records an edit unless an undo/redo is running. */
export const recordEdit = (entry: HistoryEntry) => useHistoryStore.getState().record(entry);
