import { useState } from 'react';
import { ApiError } from '../api/client';
import { useLinkCard } from '../api/queries';
import { toast } from '../ui/toastStore';
import { messageOf } from '../ui/errors';
import { LinkConflictDialog } from './LinkConflictDialog';

/** Links a card with the FR-013 conflict handling; render `dialog` somewhere in the tree. */
export function useLinkWithConflicts() {
  const link = useLinkCard();
  const [pending, setPending] = useState<{
    microserviceId: string;
    name: string;
    cardId: string;
    conflict: string;
    message: string;
    onLinked?: () => void;
  } | null>(null);

  const run = (microserviceId: string, name: string, cardId: string, replace = false, onLinked?: () => void) =>
    link.mutate(
      { microserviceId, cardId, replace },
      {
        onSuccess: () => {
          toast.success(`Spec linked to ${name}`);
          onLinked?.();
        },
        onError: (e) => {
          if (e instanceof ApiError && e.status === 409 && e.conflict) {
            setPending({ microserviceId, name, cardId, conflict: e.conflict, message: messageOf(e), onLinked });
          } else {
            toast.error(messageOf(e));
          }
        },
      },
    );

  const dialog = pending ? (
    <LinkConflictDialog
      conflict={pending.conflict}
      microserviceName={pending.name}
      message={pending.message}
      onClose={() => {
        const p = pending;
        setPending(null);
        p.onLinked?.();
      }}
      onReplace={() => {
        const p = pending;
        setPending(null);
        run(p.microserviceId, p.name, p.cardId, true, p.onLinked);
      }}
    />
  ) : null;

  return { link: run, dialog };
}
