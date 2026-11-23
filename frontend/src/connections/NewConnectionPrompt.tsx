import { useState } from 'react';
import { ApiError } from '../api/client';
import type { Flow, ConnectionKind } from '../api/types';
import { EndpointSelect } from './EndpointSelect';
import { FlowSelect } from './FlowSelect';
import { Modal } from '../ui/Modal';
import { messageOf } from '../ui/errors';

export interface NewConnectionValues {
  name: string;
  kind: ConnectionKind | null;
  description: string | null;
  flowId: string | null;
  endpoint: string | null;
  /** order in the flow; null appends (spec 004 FR-002) */
  step: number | null;
}

/** Asks for the name (required), kind and description of a connection drawn on the canvas (FR-024). */
export function NewConnectionPrompt({
  flows,
  endpoints,
  sourceName,
  targetName,
  proposed,
  onSubmit,
  onClose,
}: {
  flows: Flow[];
  /** the target card's endpoints */
  endpoints: string[];
  sourceName: string;
  targetName: string;
  proposed: boolean;
  /** Resolves when created; rejects with the API error to show it inline. */
  onSubmit: (values: NewConnectionValues) => Promise<void>;
  onClose: () => void;
}) {
  const [name, setName] = useState('');
  const [kind, setKind] = useState<ConnectionKind | ''>('');
  const [description, setDescription] = useState('');
  const [flowId, setFlowId] = useState<string | null>(null);
  const [endpoint, setEndpoint] = useState<string | null>(null);
  const [step, setStep] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      await onSubmit({
        name: name.trim(),
        kind: kind === '' ? null : kind,
        description: description.trim() === '' ? null : description,
        flowId,
        endpoint,
        step: flowId && /^\d+$/.test(step.trim()) && Number(step) >= 1 ? Number(step) : null,
      });
      onClose();
    } catch (e) {
      setError(
        e instanceof ApiError && e.conflict === 'DUPLICATE_CONNECTION'
          ? `A connection named "${name.trim()}" from ${sourceName} to ${targetName} already exists.`
          : messageOf(e),
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      title={proposed ? 'Propose a connection' : 'New connection'}
      onClose={onClose}
      footer={
        <>
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="primary" disabled={name.trim() === '' || busy} onClick={() => void submit()}>
            {proposed ? 'Propose' : 'Create'}
          </button>
        </>
      }
    >
      <p>
        <strong>{sourceName}</strong> → <strong>{targetName}</strong>
      </p>
      <label className="stack">
        Name
        <input aria-label="Connection name" value={name} maxLength={120} autoFocus placeholder="Order placed" onChange={(e) => setName(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && name.trim() && void submit()} />
      </label>
      <label className="stack">
        Kind
        <select aria-label="Connection kind" value={kind} onChange={(e) => setKind(e.target.value as ConnectionKind | '')}>
          <option value="">none</option>
          <option value="REQUEST">request (synchronous call)</option>
          <option value="EVENT">event (asynchronous message)</option>
        </select>
      </label>
      <EndpointSelect options={endpoints} value={endpoint} onChange={setEndpoint} />
      <FlowSelect flows={flows} value={flowId} onChange={setFlowId} />
      {flowId && (
        <label className="stack">
          Step in its flow (optional)
          <input aria-label="Connection step" type="number" min={1} step={1} placeholder="next step" value={step} onChange={(e) => setStep(e.target.value)} />
        </label>
      )}
      <label className="stack">
        Description (optional)
        <textarea aria-label="Connection description" value={description} maxLength={2000} rows={2} onChange={(e) => setDescription(e.target.value)} />
      </label>
      {error && (
        <div className="form-error" role="alert">
          {error}
        </div>
      )}
    </Modal>
  );
}
