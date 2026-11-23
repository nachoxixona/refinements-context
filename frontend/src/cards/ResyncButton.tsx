import { useState } from 'react';
import { ApiError } from '../api/client';
import { useSyncCard } from '../api/queries';
import { Modal } from '../ui/Modal';
import { toast } from '../ui/toastStore';
import { messageOf } from '../ui/errors';

function syncFailureMessage(error: unknown): string {
  const base = messageOf(error);
  if (error instanceof ApiError && error.errors.length > 0) {
    const first = error.errors[0];
    return `${base} ${first?.location ? first.location + ': ' : ''}${first?.message ?? ''}`;
  }
  return base;
}

/** Re-sync for URL cards; a paste dialog for pasted ones (FR-004). */
export function ResyncButton({
  cardId,
  title,
  sourceType,
  onDone,
}: {
  cardId: string;
  title: string;
  sourceType: 'PASTE' | 'URL';
  onDone?: () => void;
}) {
  const sync = useSyncCard();
  const [pasteOpen, setPasteOpen] = useState(false);
  const [content, setContent] = useState('');

  const run = (newContent?: string) =>
    sync.mutate(
      { cardId, content: newContent },
      {
        onSuccess: (summary) => {
          if (summary.unchanged) toast.info(`${summary.title}: unchanged`);
          else toast.success(`${summary.title} re-synced: ${summary.endpointCount} endpoints`);
          setPasteOpen(false);
          onDone?.();
        },
        onError: (e) => {
          toast.error(`Re-sync failed; the last good version is kept. ${syncFailureMessage(e)}`);
          onDone?.();
        },
      },
    );

  return (
    <>
      <button
        type="button"
        className="menu-item"
        title="Re-syncing replaces the card's endpoints and can't be undone with Undo"
        disabled={sync.isPending}
        onClick={() => (sourceType === 'URL' ? run() : setPasteOpen(true))}>
        {sync.isPending ? 'Re-syncing…' : 'Re-sync'}
      </button>
      {pasteOpen && (
        <Modal
          title={`Re-sync ${title}`}
          onClose={() => setPasteOpen(false)}
          wide
          footer={
            <>
              <button type="button" onClick={() => setPasteOpen(false)}>
                Cancel
              </button>
              <button type="button" className="primary" disabled={content.trim() === '' || sync.isPending} onClick={() => run(content)}>
                Re-sync
              </button>
            </>
          }
        >
          <label className="stack">
            Paste the updated spec (JSON or YAML)
            <textarea className="spec-input" value={content} onChange={(e) => setContent(e.target.value)} rows={16} />
          </label>
        </Modal>
      )}
    </>
  );
}
