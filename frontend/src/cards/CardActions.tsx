import { FloatingMenu } from '../ui/FloatingMenu';
import { useMemo, useState } from 'react';
import { useCanvas, useDeleteCard, useUnlinkCard } from '../api/queries';
import type { MicroserviceNodeData } from '../api/types';
import { useLinkWithConflicts } from '../microservices/useLinkCard';
import { useScenarioStore } from '../scenario/scenarioStore';
import { ConfirmDialog } from '../ui/Modal';
import { toast } from '../ui/toastStore';
import { messageOf } from '../ui/errors';
import { ResyncButton } from './ResyncButton';

/** The spec card menu: re-sync, link to a microservice, unlink, delete (FR-004, FR-008, FR-012). */
export function CardActions({
  cardId,
  title,
  sourceType,
  ownerId,
  draftMode,
  anchor,
  onClose,
}: {
  cardId: string;
  title: string;
  sourceType: 'PASTE' | 'URL';
  ownerId: string | null;
  draftMode: boolean;
  /** the ⋯ button the menu opens from */
  anchor: HTMLElement | null;
  onClose: () => void;
}) {
  const { data: canvas } = useCanvas();
  const del = useDeleteCard();
  const unlink = useUnlinkCard();
  const { link, dialog } = useLinkWithConflicts();
  const proposeLink = useScenarioStore((s) => s.addProposedLink);
  const proposed = useScenarioStore((s) => s.draft.proposedMicroservices);
  const [choosing, setChoosing] = useState(false);
  const [confirming, setConfirming] = useState(false);

  const targets = useMemo(() => {
    const baseline = (canvas?.nodes ?? [])
      .filter((n) => n.type === 'microservice')
      .map((n) => n.data as MicroserviceNodeData)
      .map((m) => ({ ref: m.microserviceId, name: m.name, hasCard: m.specCardId != null }));
    const extra = draftMode ? proposed.map((p) => ({ ref: p.tempKey, name: p.name, hasCard: Boolean(p.card) })) : [];
    return [...baseline, ...extra].sort((a, b) => a.name.localeCompare(b.name));
  }, [canvas, proposed, draftMode]);

  const owner = targets.find((t) => `ms:${t.ref}` === ownerId);

  return (
    <FloatingMenu anchor={anchor} onClose={onClose}>
      {!draftMode && <ResyncButton cardId={cardId} title={title} sourceType={sourceType} onDone={onClose} />}
      {!ownerId && (
        <button type="button" className="menu-item" onClick={() => setChoosing((c) => !c)}>
          {draftMode ? 'Propose linking to…' : 'Link to…'}
        </button>
      )}
      {choosing && (
        <select
          aria-label="Microservice to link to"
          className="menu-select"
          defaultValue=""
          onChange={(e) => {
            const target = targets.find((t) => t.ref === e.target.value);
            if (!target) return;
            if (draftMode) {
              proposeLink(target.ref, cardId);
              onClose();
            } else {
              setChoosing(false);
              link(target.ref, target.name, cardId, false, onClose);
            }
          }}
        >
          <option value="" disabled>
            Choose a microservice
          </option>
          {targets.map((t) => (
            <option key={t.ref} value={t.ref}>
              {t.name}
              {t.hasCard ? ' (has a spec)' : ''}
            </option>
          ))}
        </select>
      )}
      {ownerId && !draftMode && (
        <button
          type="button"
          className="menu-item"
          onClick={() =>
            unlink.mutate(ownerId.replace(/^ms:/, ''), {
              onSuccess: () => {
                toast.success(`${title} unlinked${owner ? ` from ${owner.name}` : ''}`);
                onClose();
              },
              onError: (e) => toast.error(messageOf(e)),
            })
          }
        >
          Unlink from {owner?.name ?? 'microservice'}
        </button>
      )}
      {!draftMode && (
        <button type="button" className="menu-item danger" onClick={() => setConfirming(true)}>
          Delete spec card
        </button>
      )}
      <button type="button" className="menu-item" onClick={onClose}>
        Close
      </button>
      {dialog}
      {confirming && (
        <ConfirmDialog
          title="Delete spec card"
          message={
            <p>
              Delete <strong>{title}</strong>? {ownerId ? 'Its microservice stays, without a spec. ' : ''}Connections are not affected.
              <br />
              <span className="muted">This can't be undone with Undo.</span>
            </p>
          }
          confirmLabel="Delete"
          danger
          onCancel={() => setConfirming(false)}
          onConfirm={() =>
            del.mutate(cardId, {
              onSuccess: () => {
                toast.success(`${title} deleted`);
                onClose();
              },
              onError: (e) => toast.error(messageOf(e)),
            })
          }
        />
      )}
    </FloatingMenu>
  );
}
