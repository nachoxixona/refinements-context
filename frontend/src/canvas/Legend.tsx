import type { Flow, FlowColor } from '../api/types';
import { useCanvasStore } from './canvasStore';
import { connectionStyle } from './edges/connectionStyle';

function Line({
  kind,
  marks = {},
  flow,
}: {
  kind: 'REQUEST' | 'EVENT' | null;
  marks?: { proposed?: boolean; removed?: boolean };
  flow?: FlowColor;
}) {
  return (
    <svg width="36" height="10" aria-hidden="true">
      <line x1="0" y1="5" x2="36" y2="5" style={connectionStyle(kind, marks, flow ? 3 : 2, flow)} />
    </svg>
  );
}

/**
 * Explains the connection line styles (FR-018b), lists flows (FR-051; click one to highlight its connections) and, in
 * draft mode, the proposal styles. Part of image exports.
 */
export function Legend({ draft = false, flows = [] }: { draft?: boolean; flows?: readonly Flow[] }) {
  const selectedFlowId = useCanvasStore((s) => s.selectedFlowId);
  const selectFlow = useCanvasStore((s) => s.selectFlow);
  const openPanel = useCanvasStore((s) => s.openPanel);
  const startWalkthrough = useCanvasStore((s) => s.startWalkthrough);
  const walkthrough = useCanvasStore((s) => s.walkthrough);
  return (
    <div className="legend" aria-label="Legend">
      <div className="legend-row">
        <Line kind="REQUEST" /> request
      </div>
      <div className="legend-row">
        <Line kind="EVENT" /> event
      </div>
      <div className="legend-row">
        <Line kind={null} /> no kind
      </div>
      {flows.map((g) => (
        <span key={g.id} className="legend-flow-row">
          <button
            type="button"
            className={`legend-row legend-flow${selectedFlowId === g.id || walkthrough?.flowId === g.id ? ' active' : ''}`}
            aria-pressed={selectedFlowId === g.id}
            title="Highlight this flow's connections"
            onClick={() => selectFlow(g.id)}
          >
            <Line kind="REQUEST" flow={g.color} /> {g.name}
          </button>
          <button
            type="button"
            className="legend-walk no-export"
            aria-label={`Walk through ${g.name}`}
            title="Walk through this flow step by step"
            onClick={() => startWalkthrough(g.id)}
          >
            ▶
          </button>
        </span>
      ))}
      <button type="button" className="legend-row legend-edit no-export" onClick={() => openPanel('flows')}>
        {flows.length === 0 ? '+ Flows' : 'Edit flows'}
      </button>
      {draft && (
        <>
          <div className="legend-row">
            <Line kind="EVENT" marks={{ proposed: true }} /> proposed
          </div>
          <div className="legend-row">
            <svg width="36" height="10" aria-hidden="true">
              <line x1="0" y1="5" x2="36" y2="5" style={{ stroke: '#16a34a', strokeWidth: 8, strokeOpacity: 0.35 }} />
              <line x1="0" y1="5" x2="36" y2="5" style={{ stroke: '#2563eb', strokeWidth: 2 }} />
            </svg>
            proposed, in a flow
          </div>
          <div className="legend-row">
            <Line kind="REQUEST" marks={{ removed: true }} /> <s>proposed removal</s>
          </div>
        </>
      )}
      <div className="legend-row">
        <span className="legend-swatch swatch-up" /> upstream
        <span className="legend-swatch swatch-down" /> downstream
        <span className="legend-swatch swatch-search" /> search hit
      </div>
    </div>
  );
}
