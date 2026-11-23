import type { ConnectionChangeSummary, ConnectionsDocumentChanges } from '../../api/types';
import { Modal } from '../../ui/Modal';

function ConnectionList({ title, items, className }: { title: string; items: ConnectionChangeSummary[]; className: string }) {
  if (items.length === 0) return null;
  return (
    <section className={`change-section ${className}`}>
      <h4>
        {title} ({items.length})
      </h4>
      <ul>
        {items.map((f, i) => (
          <li key={i}>
            <strong>{f.name}</strong>: {f.source} → {f.target}
            {f.kindBefore !== f.kindAfter && (f.kindBefore || f.kindAfter) && className === 'changed' && (
              <span className="muted">
                {' '}
                kind {f.kindBefore ?? 'none'} → {f.kindAfter ?? 'none'}
              </span>
            )}
            {f.descriptionBefore !== f.descriptionAfter && className === 'changed' && <span className="muted"> description changed</span>}
            {f.stepBefore !== f.stepAfter && className === 'changed' && (
              <span className="muted">
                {' '}
                step {f.stepBefore ?? '–'} → {f.stepAfter ?? '–'}
              </span>
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

/** What applying a connections document will change; nothing is applied until Confirm (FR-020, FR-021). */
export function ChangePreviewDialog({
  changes,
  busy,
  onConfirm,
  onCancel,
}: {
  changes: ConnectionsDocumentChanges;
  busy?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}) {
  const removed = changes.connectionsRemoved.length;
  return (
    <Modal
      title="Apply connections document"
      onClose={onCancel}
      wide
      footer={
        changes.unchanged ? (
          <button type="button" className="primary" onClick={onCancel}>
            Close
          </button>
        ) : (
          <>
            <button type="button" onClick={onCancel}>
              Cancel
            </button>
            <button type="button" className={removed > 0 ? 'danger' : 'primary'} disabled={busy} onClick={onConfirm}>
              {removed > 0 ? `Apply and remove ${removed} connection${removed === 1 ? '' : 's'}` : 'Apply'}
            </button>
          </>
        )
      }
    >
      {changes.unchanged ? (
        <p className="no-changes">No changes. The document matches the canvas.</p>
      ) : (
        <>
          {removed > 0 && (
            <p className="note warning">
              The document replaces all connections: {removed} connection{removed === 1 ? '' : 's'} not in it will be removed.
            </p>
          )}
          {changes.microservicesAdded.length > 0 && (
            <section className="change-section added">
              <h4>New microservices ({changes.microservicesAdded.length})</h4>
              <ul>
                {changes.microservicesAdded.map((m) => (
                  <li key={m}>{m}</li>
                ))}
              </ul>
            </section>
          )}
          {changes.microservicesChanged.length > 0 && (
            <section className="change-section changed">
              <h4>Microservices changed ({changes.microservicesChanged.length})</h4>
              <ul>
                {changes.microservicesChanged.map((m) => {
                  const what = [
                    m.descriptionBefore !== m.descriptionAfter && 'description',
                    (m.teamBefore ?? null) !== (m.teamAfter ?? null) && `team ${m.teamBefore ?? '–'} → ${m.teamAfter ?? '–'}`,
                    (m.repositoryBefore ?? null) !== (m.repositoryAfter ?? null) && 'repository',
                    (m.docsBefore ?? null) !== (m.docsAfter ?? null) && 'docs',
                  ].filter(Boolean);
                  return (
                    <li key={m.name}>
                      {m.name}
                      {what.length > 0 && <span className="muted"> ({what.join(', ')})</span>}
                    </li>
                  );
                })}
              </ul>
            </section>
          )}
          <ConnectionList title="Connections added" items={changes.connectionsAdded} className="added" />
          <ConnectionList title="Connections changed" items={changes.connectionsChanged} className="changed" />
          <ConnectionList title="Connections removed" items={changes.connectionsRemoved} className="removed" />
        </>
      )}
    </Modal>
  );
}
