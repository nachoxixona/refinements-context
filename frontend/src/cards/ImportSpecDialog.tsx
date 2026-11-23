import { useState } from 'react';
import { useReactFlow } from '@xyflow/react';
import { ApiError } from '../api/client';
import { useImportCard } from '../api/queries';
import type { ImportCardRequest, ParseError } from '../api/types';
import { Modal } from '../ui/Modal';
import { toast } from '../ui/toastStore';
import { messageOf } from '../ui/errors';

type Tab = 'PASTE' | 'URL';

/** Import a spec as an unlinked card by paste or URL (FR-001, FR-002), with located errors (FR-005). */
export function ImportSpecDialog({ onClose }: { onClose: () => void }) {
  const importCard = useImportCard();
  const rf = useReactFlow();
  const [tab, setTab] = useState<Tab>('PASTE');
  const [content, setContent] = useState('');
  const [url, setUrl] = useState('');
  const [errors, setErrors] = useState<ParseError[]>([]);
  const [message, setMessage] = useState<string | null>(null);
  const [duplicateOf, setDuplicateOf] = useState<string | null>(null);

  const submit = () => {
    setErrors([]);
    setMessage(null);
    setDuplicateOf(null);
    const body: ImportCardRequest = tab === 'PASTE' ? { sourceType: 'PASTE', content } : { sourceType: 'URL', url: url.trim() };
    importCard.mutate(body, {
      onSuccess: (card) => {
        toast.success(`${card.title} imported with ${card.endpointCount} endpoints`);
        onClose();
      },
      onError: (e) => {
        if (e instanceof ApiError && e.status === 409 && e.conflict === 'DUPLICATE_CARD') {
          setDuplicateOf(e.existingCardId ?? null);
          setMessage(messageOf(e));
          return;
        }
        if (e instanceof ApiError && e.status === 422) {
          setErrors(e.errors);
          setMessage('The spec could not be imported:');
          return;
        }
        setMessage(messageOf(e));
      },
    });
  };

  const showCard = () => {
    if (!duplicateOf) return;
    onClose();
    void rf.fitView({ nodes: [{ id: `card:${duplicateOf}` }], duration: 400, maxZoom: 1.1, padding: 0.4 });
  };

  const canSubmit = tab === 'PASTE' ? content.trim() !== '' : url.trim() !== '';

  return (
    <Modal
      title="Import spec"
      onClose={onClose}
      wide
      footer={
        <>
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="primary" disabled={!canSubmit || importCard.isPending} onClick={submit}>
            {importCard.isPending ? 'Importing…' : 'Import'}
          </button>
        </>
      }
    >
      <p className="muted">The spec becomes a card on the canvas. Importing never creates connections; link the card to a microservice afterwards. Importing can't be undone with Undo; delete the card instead.</p>
      <div className="tabs" role="tablist">
        {(['PASTE', 'URL'] as const).map((t) => (
          <button key={t} type="button" role="tab" aria-selected={tab === t} className={tab === t ? 'tab active' : 'tab'} onClick={() => setTab(t)}>
            {t === 'PASTE' ? 'Paste' : 'URL'}
          </button>
        ))}
      </div>
      {tab === 'PASTE' ? (
        <label className="stack">
          OpenAPI 3.x or Swagger 2.0 spec (JSON or YAML)
          <textarea className="spec-input" aria-label="Spec content" value={content} onChange={(e) => setContent(e.target.value)} rows={16} placeholder="openapi: 3.0.3&#10;info:&#10;  title: Order Service&#10;…" />
        </label>
      ) : (
        <label className="stack">
          URL of the live spec (http or https)
          <input type="url" aria-label="Spec URL" value={url} onChange={(e) => setUrl(e.target.value)} placeholder="http://orders.internal/v3/api-docs" />
        </label>
      )}
      {message && (
        <div className="form-error" role="alert">
          <p>{message}</p>
          {duplicateOf && (
            <button type="button" onClick={showCard}>
              Show card
            </button>
          )}
          {errors.length > 0 && (
            <ul>
              {errors.map((err, i) => (
                <li key={i}>
                  {err.location ? <code>{err.location}</code> : null}
                  {err.location ? ': ' : ''}
                  {err.message}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Modal>
  );
}
