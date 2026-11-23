import { useEffect } from 'react';
import { toast } from '../ui/toastStore';
import { installDraftHistory } from './draftHistory';
import { useHistoryStore } from './historyStore';

const typing = (t: EventTarget | null) =>
  t instanceof HTMLElement && (t.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(t.tagName) || t.closest('.cm-editor') !== null);

/**
 * Undo / redo buttons and shortcuts (spec 004 FR-008): Ctrl/⌘+Z undoes, Ctrl/⌘+Shift+Z or Ctrl+Y redoes. Shortcuts are
 * ignored while typing, so text fields keep their own undo.
 */
export function UndoButtons() {
  const past = useHistoryStore((s) => s.past);
  const future = useHistoryStore((s) => s.future);
  const running = useHistoryStore((s) => s.running);
  const undo = useHistoryStore((s) => s.undo);
  const redo = useHistoryStore((s) => s.redo);

  useEffect(() => {
    useHistoryStore.setState({ onError: (m) => toast.error(m) });
    return installDraftHistory();
  }, []);

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!(e.ctrlKey || e.metaKey) || e.altKey || typing(e.target)) return;
      const key = e.key.toLowerCase();
      if (key === 'z' && !e.shiftKey) {
        e.preventDefault();
        void undo();
      } else if ((key === 'z' && e.shiftKey) || (key === 'y' && !e.metaKey)) {
        e.preventDefault();
        void redo();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [undo, redo]);

  const last = past[past.length - 1];
  const next = future[future.length - 1];
  return (
    <span className="undo-buttons">
      <button
        type="button"
        className="icon-button"
        disabled={!last || running}
        aria-label={last ? `Undo ${last.label}` : 'Nothing to undo'}
        title={last ? `Undo: ${last.label} (Ctrl+Z)` : 'Nothing to undo'}
        onClick={() => void undo()}
      >
        ↶
      </button>
      <button
        type="button"
        className="icon-button"
        disabled={!next || running}
        aria-label={next ? `Redo ${next.label}` : 'Nothing to redo'}
        title={next ? `Redo: ${next.label} (Ctrl+Shift+Z)` : 'Nothing to redo'}
        onClick={() => void redo()}
      >
        ↷
      </button>
    </span>
  );
}
