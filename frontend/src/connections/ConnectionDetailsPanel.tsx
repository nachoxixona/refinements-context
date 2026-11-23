import { useEffect, useState } from 'react';
import { useDeleteConnection, useUpdateConnection } from '../api/queries';
import type { ConnectionKind } from '../api/types';
import { useCanvasStore } from '../canvas/canvasStore';
import { kindLabel } from '../canvas/edges/connectionStyle';
import { useCanvasView } from '../canvas/useCanvasView';
import { isMicroservice, targetEndpoints } from '../canvas/viewModel';
import { useScenarioStore } from '../scenario/scenarioStore';
import { ConfirmDialog } from '../ui/Modal';
import { EndpointSelect } from './EndpointSelect';
import { FlowSelect } from './FlowSelect';
import { toast } from '../ui/toastStore';
import { messageOf } from '../ui/errors';

/** Details of the selected connection; edit, delete, or in draft mode propose a change or removal (FR-018a, FR-037a). */
export function ConnectionDetailsPanel() {
  const selected = useCanvasStore((s) => s.selectedEdgeId);
  const selectEdge = useCanvasStore((s) => s.selectEdge);
  const { view, isDraft } = useCanvasView();
  const update = useUpdateConnection();
  const del = useDeleteConnection();
  const toggleRemoval = useScenarioStore((s) => s.toggleRemoval);
  const setChange = useScenarioStore((s) => s.setConnectionChange);
  const clearChange = useScenarioStore((s) => s.clearConnectionChange);
  const updateProposed = useScenarioStore((s) => s.updateProposedConnection);
  const removeProposed = useScenarioStore((s) => s.removeProposedConnection);
  const edge = view.edges.find((e) => e.id === selected);
  const [name, setName] = useState('');
  const [kind, setKind] = useState<ConnectionKind | ''>('');
  const [description, setDescription] = useState('');
  const [flowId, setFlowId] = useState<string | null>(null);
  const [endpoint, setEndpoint] = useState<string | null>(null);
  const [step, setStep] = useState('');
  const [confirming, setConfirming] = useState(false);

  useEffect(() => {
    if (!edge) return;
    setName(edge.data.name);
    setKind(edge.data.kind ?? '');
    setDescription(edge.data.description ?? '');
    setFlowId(edge.data.flowId);
    setEndpoint(edge.data.endpoint);
    setStep(edge.data.step != null ? String(edge.data.step) : '');
  }, [edge?.id, edge?.data.name, edge?.data.kind, edge?.data.description, edge?.data.flowId, edge?.data.endpoint, edge?.data.step]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!edge) return <p className="muted panel-body">The connection is no longer on the canvas.</p>;
  const nameOf = (id: string) => {
    const n = view.nodes.find((x) => x.id === id);
    return n && isMicroservice(n.data) ? n.data.name : id;
  };
  const d = edge.data;
  const stepValue = flowId && /^\d+$/.test(step.trim()) && Number(step) >= 1 ? Number(step) : null;
  const stepInvalid = flowId !== null && step.trim() !== '' && stepValue === null;
  const stepChanged = flowId !== null && stepValue !== null && stepValue !== (d.step ?? null);
  const changed =
    stepChanged ||
    name.trim() !== d.name ||
    (kind === '' ? null : kind) !== d.kind ||
    (description.trim() === '' ? null : description) !== d.description ||
    flowId !== d.flowId ||
    endpoint !== d.endpoint;

  const save = () => {
    const values = { name: name.trim(), kind: kind === '' ? null : kind, description: description.trim() === '' ? null : description, flowId, endpoint };
    if (d.proposed) {
      updateProposed(d.connectionId, { ...values, step: flowId ? stepValue : null });
      return;
    }
    if (isDraft) {
      const original = d.before ?? d;
      setChange(d.connectionId, {
        name: values.name !== original.name ? values.name : undefined,
        kind: values.kind === original.kind ? undefined : (values.kind ?? 'NONE'),
        description: values.description !== original.description ? values.description : undefined,
        flowId: values.flowId === original.flowId ? undefined : (values.flowId ?? 'NONE'),
        endpoint: values.endpoint === original.endpoint ? undefined : (values.endpoint ?? 'NONE'),
        step: stepValue !== null && stepValue !== (original.step ?? null) ? stepValue : undefined,
      });
      return;
    }
    update.mutate(
      {
        id: d.connectionId,
        // a step joins the patch only when it changes; a new flow without one is appended (FR-002)
        patch: stepChanged ? { ...values, step: stepValue } : values,
      },
      { onSuccess: () => toast.success('Connection updated'), onError: (e) => toast.error(messageOf(e)) },
    );
  };

  return (
    <div className="panel-body">
      <h3 className="panel-title">
        {d.proposed ? 'Proposed connection' : 'Connection'}
        {d.removed && <span className="removed-tag"> proposed removal</span>}
      </h3>
      <p>
        <strong>{nameOf(edge.source)}</strong> → <strong>{nameOf(edge.target)}</strong>
      </p>
      {d.before && (
        <p className="note">
          Proposed change from <em>{d.before.name}</em> ({kindLabel(d.before.kind)}).{' '}
          <button type="button" onClick={() => clearChange(d.connectionId)}>
            Undo change
          </button>
        </p>
      )}
      <label className="stack">
        Name
        <input aria-label="Connection name" value={name} maxLength={120} disabled={d.removed} onChange={(e) => setName(e.target.value)} />
      </label>
      <label className="stack">
        Kind
        <select aria-label="Connection kind" value={kind} disabled={d.removed} onChange={(e) => setKind(e.target.value as ConnectionKind | '')}>
          <option value="">none</option>
          <option value="REQUEST">request</option>
          <option value="EVENT">event</option>
        </select>
      </label>
      <EndpointSelect options={targetEndpoints(view, edge.target)} value={endpoint} onChange={setEndpoint} disabled={d.removed} />
      {d.endpointMissing && <p className="note warning">This endpoint is not in the target's spec.</p>}
      <FlowSelect flows={view.flows} value={flowId} onChange={setFlowId} disabled={d.removed} />
      <label className="stack">
        Step in its flow
        <input
          aria-label="Connection step"
          type="number"
          min={1}
          step={1}
          value={flowId ? step : ''}
          placeholder={flowId ? 'next step' : 'choose a flow first'}
          disabled={d.removed || !flowId}
          onChange={(e) => setStep(e.target.value)}
        />
      </label>
      {stepInvalid && <p className="note warning">A step is a whole number of at least 1.</p>}
      {flowId && <p className="muted small">Connections with the same step happen in parallel.</p>}
      <label className="stack">
        Description
        <textarea aria-label="Connection description" value={description} rows={3} maxLength={2000} disabled={d.removed} onChange={(e) => setDescription(e.target.value)} />
      </label>
      <div className="panel-actions">
        <button type="button" className="primary" disabled={!changed || name.trim() === '' || d.removed || stepInvalid} onClick={save}>
          {isDraft && !d.proposed ? 'Propose change' : 'Save'}
        </button>
        {d.proposed ? (
          <button
            type="button"
            className="danger"
            onClick={() => {
              removeProposed(d.connectionId);
              selectEdge(null);
            }}
          >
            Remove proposal
          </button>
        ) : isDraft ? (
          <button type="button" onClick={() => toggleRemoval('connections', d.connectionId)}>
            {d.removed ? 'Undo proposed removal' : 'Propose removal'}
          </button>
        ) : (
          <button type="button" className="danger" onClick={() => setConfirming(true)}>
            Delete connection
          </button>
        )}
      </div>
      {confirming && (
        <ConfirmDialog
          title="Delete connection"
          message={<p>Delete the connection "{d.name}"? The connections document will no longer include it.</p>}
          confirmLabel="Delete"
          danger
          onCancel={() => setConfirming(false)}
          onConfirm={() =>
            del.mutate(d.connectionId, {
              onSuccess: () => {
                toast.success('Connection deleted');
                selectEdge(null);
              },
              onError: (e) => toast.error(messageOf(e)),
            })
          }
        />
      )}
    </div>
  );
}
