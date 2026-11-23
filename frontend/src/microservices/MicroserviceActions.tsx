import { FloatingMenu } from '../ui/FloatingMenu';
import { useState } from 'react';
import { useDeleteMicroservice, useUnlinkCard } from '../api/queries';
import { useScenarioStore } from '../scenario/scenarioStore';
import { ConfirmDialog } from '../ui/Modal';
import { toast } from '../ui/toastStore';
import { messageOf } from '../ui/errors';
import { MicroserviceForm } from './MicroserviceForm';

/** The microservice menu: edit, unlink, delete; in draft mode, propose removals (FR-037a). */
export function MicroserviceActions({
  microserviceId,
  name,
  description,
  ownership,
  hasCard,
  connectionCount,
  draftMode,
  removed,
  linkRemoved,
  anchor,
  onClose,
}: {
  ownership?: import('./MicroserviceForm').Ownership;
  microserviceId: string;
  name: string;
  description: string | null;
  hasCard: boolean;
  connectionCount: number;
  draftMode: boolean;
  removed: boolean;
  linkRemoved: boolean;
  /** the ⋯ button the menu opens from */
  anchor: HTMLElement | null;
  onClose: () => void;
}) {
  const del = useDeleteMicroservice();
  const unlink = useUnlinkCard();
  const toggleRemoval = useScenarioStore((s) => s.toggleRemoval);
  const [editing, setEditing] = useState(false);
  const [confirming, setConfirming] = useState(false);

  return (
    <FloatingMenu anchor={anchor} onClose={onClose}>
      {draftMode ? (
        <>
          <button
            type="button"
            className="menu-item"
            onClick={() => {
              toggleRemoval('microservices', microserviceId);
              onClose();
            }}
          >
            {removed ? 'Undo proposed removal' : 'Propose removal'}
          </button>
          {hasCard && !removed && (
            <button
              type="button"
              className="menu-item"
              onClick={() => {
                toggleRemoval('links', microserviceId);
                onClose();
              }}
            >
              {linkRemoved ? 'Undo proposed unlink' : 'Propose unlinking its spec'}
            </button>
          )}
        </>
      ) : (
        <>
          <button type="button" className="menu-item" onClick={() => setEditing(true)}>
            Edit details and ownership
          </button>
          {hasCard && (
            <button
              type="button"
              className="menu-item"
              onClick={() =>
                unlink.mutate(microserviceId, {
                  onSuccess: () => {
                    toast.success(`Spec unlinked from ${name}`);
                    onClose();
                  },
                  onError: (e) => toast.error(messageOf(e)),
                })
              }
            >
              Unlink spec
            </button>
          )}
          <button type="button" className="menu-item danger" onClick={() => setConfirming(true)}>
            Delete microservice
          </button>
        </>
      )}
      <button type="button" className="menu-item" onClick={onClose}>
        Close
      </button>
      {editing && (
        <MicroserviceForm
          existing={{ id: microserviceId, name, description, ...ownership }}
          onClose={() => {
            setEditing(false);
            onClose();
          }}
        />
      )}
      {confirming && (
        <ConfirmDialog
          title="Delete microservice"
          message={
            <p>
              Delete <strong>{name}</strong>?{' '}
              {connectionCount > 0
                ? `${connectionCount} connection${connectionCount === 1 ? '' : 's'} reference it; they are kept and listed as dangling for review.`
                : 'No connections reference it.'}{' '}
              {hasCard && 'Its spec stays on the canvas, unlinked.'}
            </p>
          }
          confirmLabel="Delete"
          danger
          onCancel={() => setConfirming(false)}
          onConfirm={() =>
            del.mutate(microserviceId, {
              onSuccess: (r) => {
                toast.success(
                  r.danglingConnectionIds.length > 0 ? `${name} deleted; ${r.danglingConnectionIds.length} connection(s) are now dangling` : `${name} deleted`,
                );
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
