import { useState } from 'react';
import { ApiError } from '../api/client';
import { useCanvas, useCreateFlow, useDeleteFlow, useSetFlowSteps, useUpdateFlow } from '../api/queries';
import { useCanvasStore } from '../canvas/canvasStore';
import { baselineView, isMicroservice, type GraphView } from '../canvas/viewModel';
import { flowSteps, reorderSteps } from '../walkthrough/steps';
import type { Flow, FlowColor } from '../api/types';
import { FLOW_COLORS } from '../canvas/edges/connectionStyle';
import { useScenarioStore } from '../scenario/scenarioStore';
import { ConfirmDialog } from '../ui/Modal';
import { toast } from '../ui/toastStore';
import { messageOf } from '../ui/errors';

const COLORS = Object.keys(FLOW_COLORS) as FlowColor[];

function ColorSelect({
  value,
  onChange,
  label,
}: {
  value: FlowColor;
  onChange: (c: FlowColor) => void;
  label: string;
}) {
  return (
    <span className="row">
      <span className="flow-swatch" style={{ background: FLOW_COLORS[value] }} aria-hidden="true" />
      <select aria-label={label} value={value} onChange={(e) => onChange(e.target.value as FlowColor)}>
        {COLORS.map((c) => (
          <option key={c} value={c}>
            {c.toLowerCase()}
          </option>
        ))}
      </select>
    </span>
  );
}

/** Create, rename, recolor and delete flows (FR-049). Flows are baseline data: not editable in drafts. */
export function FlowsPanel() {
  const { data: canvas } = useCanvas();
  const isDraft = useScenarioStore((s) => s.isDraftMode);
  const create = useCreateFlow();
  const flows = canvas?.flows ?? [];
  const [name, setName] = useState('');
  const [color, setColor] = useState<FlowColor>(COLORS[flows.length % COLORS.length] ?? 'BLUE');
  const counts = new Map<string, number>();
  for (const e of canvas?.edges ?? [])
    if (e.data.flowId) counts.set(e.data.flowId, (counts.get(e.data.flowId) ?? 0) + 1);

  const add = () =>
    create.mutate(
      { name: name.trim(), color },
      {
        onSuccess: () => {
          setName('');
          setColor(COLORS[(flows.length + 1) % COLORS.length] ?? 'BLUE');
        },
        onError: (e) =>
          toast.error(
            e instanceof ApiError && e.status === 409
              ? `A flow named "${name.trim()}" already exists.`
              : messageOf(e),
          ),
      },
    );

  return (
    <div className="panel-body">
      <h3 className="panel-title">Flows</h3>
      <p className="muted">
        Give related connections a flow so a business process is easy to follow. Click a flow in the legend to
        highlight it.
      </p>
      {isDraft && (
        <p className="note">
          Flows are edited outside Draft Scenario Mode; proposed connections can still use them.
        </p>
      )}
      <ul className="plain-list">
        {flows.map((g) => (
          <FlowRow
            key={g.id}
            flow={g}
            connections={counts.get(g.id) ?? 0}
            disabled={isDraft}
            view={canvas ? baselineView(canvas) : null}
          />
        ))}
      </ul>
      {!isDraft && (
        <div className="endpoint-form">
          <input
            aria-label="New flow name"
            placeholder="Checkout"
            value={name}
            maxLength={120}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && name.trim() && add()}
          />
          <ColorSelect value={color} onChange={setColor} label="New flow color" />
          <button
            type="button"
            className="primary"
            disabled={name.trim() === '' || create.isPending}
            onClick={add}
          >
            Add flow
          </button>
        </div>
      )}
    </div>
  );
}

function FlowRow({
  flow,
  connections,
  disabled,
  view,
}: {
  flow: Flow;
  connections: number;
  disabled: boolean;
  view: GraphView | null;
}) {
  const update = useUpdateFlow();
  const del = useDeleteFlow();
  const setSteps = useSetFlowSteps();
  const startWalkthrough = useCanvasStore((s) => s.startWalkthrough);
  const [name, setName] = useState(flow.name);
  const [confirming, setConfirming] = useState(false);
  const [open, setOpen] = useState(false);
  const onError = (e: unknown) => toast.error(messageOf(e));
  const steps = view ? flowSteps(view.edges, flow.id) : [];
  const edgeById = new Map((view?.edges ?? []).map((e) => [e.id, e]));
  const msName = (id: string) => {
    const n = view?.nodes.find((x) => x.id === id);
    return n && isMicroservice(n.data) ? n.data.name : '?';
  };
  const move = (index: number, delta: -1 | 1) =>
    setSteps.mutate(
      {
        flowId: flow.id,
        steps: reorderSteps(steps, index, delta, (id) => edgeById.get(id)!.data.connectionId),
      },
      { onError },
    );
  return (
    <li className="flow-item">
      <div className="list-row flow-row">
        <input
          aria-label={`Name of flow ${flow.name}`}
          value={name}
          disabled={disabled}
          maxLength={120}
          onChange={(e) => setName(e.target.value)}
          onBlur={() =>
            name.trim() &&
            name.trim() !== flow.name &&
            update.mutate({ id: flow.id, patch: { name: name.trim() } }, { onError })
          }
        />
        {disabled ? (
          <span className="flow-swatch" style={{ background: FLOW_COLORS[flow.color] }} aria-hidden="true" />
        ) : (
          <ColorSelect
            value={flow.color}
            label={`Color of flow ${flow.name}`}
            onChange={(c) => update.mutate({ id: flow.id, patch: { color: c } }, { onError })}
          />
        )}
        <span className="muted">
          {connections} connection{connections === 1 ? '' : 's'}
        </span>
        {!disabled && (
          <button type="button" className="danger" onClick={() => setConfirming(true)}>
            Delete
          </button>
        )}
        {connections > 0 && (
          <button
            type="button"
            aria-expanded={open}
            aria-label={`Steps of ${flow.name}`}
            onClick={() => setOpen((o) => !o)}
          >
            {open ? 'Hide steps' : 'Steps'}
          </button>
        )}
        {confirming && (
          <ConfirmDialog
            title="Delete flow"
            message={
              <p>
                Delete the flow "{flow.name}"? Its {connections} connection(s) are kept, without a flow.
              </p>
            }
            confirmLabel="Delete"
            danger
            onCancel={() => setConfirming(false)}
            onConfirm={() => del.mutate(flow.id, { onSuccess: () => setConfirming(false), onError })}
          />
        )}
      </div>
      {open && steps.length > 0 && (
        <div className="flow-steps">
          <ol className="flow-step-list" aria-label={`Steps of flow ${flow.name}`}>
            {steps.map((s, i) => (
              <li key={s.edgeIds.join(',')} className="flow-step">
                <span className="step-badge" style={{ background: FLOW_COLORS[flow.color] }}>
                  {i + 1}
                </span>
                <span className="grow">
                  {s.edgeIds.map((id) => {
                    const e = edgeById.get(id)!;
                    return (
                      <span key={id} className="flow-step-connection">
                        {msName(e.source)} → {msName(e.target)}: {e.data.name}
                      </span>
                    );
                  })}
                  {s.step === null && <span className="muted"> (no step yet)</span>}
                </span>
                {!disabled && (
                  <>
                    <button
                      type="button"
                      aria-label={`Move step ${i + 1} of ${flow.name} up`}
                      disabled={i === 0 || setSteps.isPending}
                      onClick={() => move(i, -1)}
                    >
                      ▲
                    </button>
                    <button
                      type="button"
                      aria-label={`Move step ${i + 1} of ${flow.name} down`}
                      disabled={i === steps.length - 1 || setSteps.isPending}
                      onClick={() => move(i, 1)}
                    >
                      ▼
                    </button>
                  </>
                )}
              </li>
            ))}
          </ol>
          <button type="button" onClick={() => startWalkthrough(flow.id)}>
            ▶ Walk through
          </button>
        </div>
      )}
    </li>
  );
}
