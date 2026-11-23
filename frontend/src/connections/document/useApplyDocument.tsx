import { useState } from 'react';
import { ApiError } from '../../api/client';
import { previewConnectionsDocument, useApplyConnectionsDocument } from '../../api/queries';
import type { DocumentError, ConnectionsDocumentChanges } from '../../api/types';
import { toast } from '../../ui/toastStore';
import { messageOf } from '../../ui/errors';
import { ChangePreviewDialog } from './ChangePreviewDialog';

/**
 * Preview → confirm → apply, shared by file upload and the editor (FR-020, FR-022). Apply sends the revision
 * the preview was computed at, so a canvas change in between returns 409 instead of being replaced.
 */
export function useApplyDocument(options: {
  onApplied?: () => void;
  onInvalid?: (errors: DocumentError[]) => void;
  onStale?: () => void;
}) {
  const apply = useApplyConnectionsDocument();
  const [pending, setPending] = useState<{ text: string; revision: number; changes: ConnectionsDocumentChanges } | null>(null);

  const start = async (text: string) => {
    try {
      const preview = await previewConnectionsDocument(text);
      setPending({ text, revision: preview.revision, changes: preview.changes });
    } catch (e) {
      if (e instanceof ApiError && e.status === 422 && e.documentErrors.length > 0) {
        if (options.onInvalid) options.onInvalid(e.documentErrors);
        else toast.error(`${messageOf(e)} ${e.documentErrors.slice(0, 3).map(describe).join('; ')}`);
        return;
      }
      toast.error(messageOf(e));
    }
  };

  const confirm = () => {
    if (!pending) return;
    apply.mutate(
      { text: pending.text, baseRevision: pending.revision },
      {
        onSuccess: (r) => {
          setPending(null);
          const c = r.applied.changes;
          toast.success(
            `Connections applied: ${c.connectionsAdded.length} added, ${c.connectionsChanged.length} changed, ${c.connectionsRemoved.length} removed`,
          );
          options.onApplied?.();
        },
        onError: (e) => {
          setPending(null);
          if (e instanceof ApiError && e.status === 409) {
            toast.error('The connections changed since the preview (for example on the canvas). Nothing was applied.');
            options.onStale?.();
            return;
          }
          if (e instanceof ApiError && e.status === 422 && options.onInvalid) {
            options.onInvalid(e.documentErrors);
            return;
          }
          toast.error(messageOf(e));
        },
      },
    );
  };

  const dialog = pending ? (
    <ChangePreviewDialog changes={pending.changes} busy={apply.isPending} onConfirm={confirm} onCancel={() => setPending(null)} />
  ) : null;

  return { start, dialog };
}

export function describe(e: DocumentError): string {
  const at = e.line ? ` (line ${e.line}${e.column ? `:${e.column}` : ''})` : '';
  return `${e.message}${at}`;
}
