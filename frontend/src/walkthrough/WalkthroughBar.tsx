import { useEffect, useMemo } from 'react';
import { useReactFlow } from '@xyflow/react';
import { useCanvasStore } from '../canvas/canvasStore';
import { FLOW_COLORS } from '../canvas/edges/connectionStyle';
import { isMicroservice, type GraphView } from '../canvas/viewModel';
import { flowSteps } from './steps';

const typing = (t: EventTarget | null) =>
  t instanceof HTMLElement && (t.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(t.tagName));

/**
 * The walkthrough bar (spec 004 FR-005): names the flow and step, lists what happens at this step, moves with
 * Prev/Next or ←/→ and ends with End or Esc. The canvas pans to the current step.
 */
export function WalkthroughBar({ view }: { view: GraphView }) {
  const walkthrough = useCanvasStore((s) => s.walkthrough);
  const setStep = useCanvasStore((s) => s.setWalkthroughStep);
  const end = useCanvasStore((s) => s.endWalkthrough);
  const rf = useReactFlow();
  const flow = view.flows.find((f) => f.id === walkthrough?.flowId);
  const steps = useMemo(() => (walkthrough ? flowSteps(view.edges, walkthrough.flowId) : []), [view.edges, walkthrough]);
  const index = walkthrough ? Math.min(walkthrough.index, Math.max(steps.length - 1, 0)) : 0;
  const current = steps[index];

  // the flow was deleted, or lost all its connections: end (spec 004 edge cases)
  useEffect(() => {
    if (walkthrough && (!flow || steps.length === 0)) end();
  }, [walkthrough, flow, steps.length, end]);

  const nodeKey = current?.nodeIds.join(',');
  useEffect(() => {
    if (!nodeKey) return;
    void rf.fitView({ nodes: nodeKey.split(',').map((id) => ({ id })), duration: 450, padding: 0.35, maxZoom: 1.1 });
  }, [nodeKey, rf]);

  useEffect(() => {
    if (!walkthrough) return;
    const onKey = (e: KeyboardEvent) => {
      if (typing(e.target) || e.altKey || e.ctrlKey || e.metaKey) return;
      if (e.key === 'ArrowRight' && index < steps.length - 1) {
        e.preventDefault();
        setStep(index + 1);
      } else if (e.key === 'ArrowLeft' && index > 0) {
        e.preventDefault();
        setStep(index - 1);
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [walkthrough, index, steps.length, setStep]);

  if (!walkthrough || !flow || !current) return null;
  const name = (id: string) => {
    const n = view.nodes.find((x) => x.id === id);
    return n && isMicroservice(n.data) ? n.data.name : id;
  };
  const edges = current.edgeIds.map((id) => view.edges.find((e) => e.id === id)).filter((e) => e !== undefined);
  return (
    <div className="walkthrough-bar" role="region" aria-label="Flow walkthrough" data-testid="walkthrough-bar">
      <div className="walkthrough-head">
        <span className="flow-swatch" style={{ background: FLOW_COLORS[flow.color] }} aria-hidden="true" />
        <strong>{flow.name}</strong>
        <span data-testid="walkthrough-position">
          · step {index + 1} of {steps.length}
        </span>
        {current.edgeIds.length > 1 && <span className="muted">({current.edgeIds.length} in parallel)</span>}
      </div>
      <ul className="walkthrough-steps">
        {edges.map((e) => (
          <li key={e.id}>
            {name(e.source)} → {name(e.target)}: <strong>{e.data.name}</strong>
            {e.data.endpoint && <code className="walkthrough-endpoint"> {e.data.endpoint}</code>}
            {e.data.description && <span className="muted"> — {e.data.description}</span>}
          </li>
        ))}
      </ul>
      <div className="walkthrough-actions">
        <button type="button" onClick={() => setStep(index - 1)} disabled={index === 0} aria-label="Previous step">
          ← Prev
        </button>
        <button type="button" className="primary" onClick={() => setStep(index + 1)} disabled={index >= steps.length - 1} aria-label="Next step">
          Next →
        </button>
        <button type="button" onClick={end}>
          End
        </button>
        <span className="muted">← → to move, Esc to end</span>
      </div>
    </div>
  );
}
