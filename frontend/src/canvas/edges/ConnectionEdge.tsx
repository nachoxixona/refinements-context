import { memo } from 'react';
import { BaseEdge, EdgeLabelRenderer, type EdgeProps } from '@xyflow/react';
import { useCanvasStore } from '../canvasStore';
import type { HttpMethod } from '../../api/types';
import { MethodBadge } from '../nodes/MethodBadge';
import type { ConnectionRfEdge } from './edgeTypes';
import { connectionStyle, proposalHalo, FLOW_COLORS, kindLabel } from './connectionStyle';

export function useEdgeWidth(active: boolean): number {
  const presentation = useCanvasStore((s) => s.presentationMode);
  const base = presentation ? 2.6 : 1.8;
  return active ? base + 1.6 : base;
}

/** Quadratic path whose middle sits `offset` px to the left of the straight line (research R11). */
export function connectionPath(sx: number, sy: number, tx: number, ty: number, offset: number) {
  const dx = tx - sx;
  const dy = ty - sy;
  const len = Math.hypot(dx, dy) || 1;
  const px = -dy / len;
  const py = dx / len;
  const mx = (sx + tx) / 2;
  const my = (sy + ty) / 2;
  const cx = mx + px * offset * 2;
  const cy = my + py * offset * 2;
  // the point 78% of the way along the curve: where the target's endpoint is shown (FR-055)
  const t = 0.78;
  const u = 1 - t;
  const endpointX = u * u * sx + 2 * u * t * cx + t * t * tx;
  const endpointY = u * u * sy + 2 * u * t * cy + t * t * ty;
  return {
    path: `M ${sx},${sy} Q ${cx},${cy} ${tx},${ty}`,
    labelX: mx + px * offset,
    labelY: my + py * offset,
    endpointX,
    endpointY,
  };
}

/** `POST /invoices` → method and path; anything unexpected is shown as a path. */
export function splitEndpoint(endpoint: string): { method: HttpMethod | null; path: string } {
  const m = /^([A-Z]+)\s+(.*)$/.exec(endpoint.trim());
  const methods: HttpMethod[] = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'TRACE'];
  return m && methods.includes(m[1] as HttpMethod) ? { method: m[1] as HttpMethod, path: m[2] ?? '' } : { method: null, path: endpoint };
}

/** One declared connection (or a bundle of them when zoomed out). */
function ConnectionEdgeImpl(props: EdgeProps<ConnectionRfEdge>) {
  const { id, sourceX, sourceY, targetX, targetY, markerEnd, data, selected } = props;
  const { path, labelX, labelY, endpointX, endpointY } = connectionPath(sourceX, sourceY, targetX, targetY, data?.offset ?? 0);
  const width = useEdgeWidth(data?.highlight === 'active' || Boolean(selected));
  const selectEdge = useCanvasStore((s) => s.selectEdge);
  if (!data) return null;
  const style = connectionStyle(data.kind, data, width, data.flowColor);
  const halo = proposalHalo(data, data.flowColor, width);
  const changedName = data.before && data.before.name !== data.name;
  const changedKind = data.before && data.before.kind !== data.kind;
  const classes = ['edge-label'];
  if (data.proposed) classes.push('edge-label-proposed');
  if (data.removed) classes.push('edge-label-removed');
  if (data.orphaned) classes.push('edge-label-orphan');
  if (data.before) classes.push('edge-label-changed');
  return (
    <g className={`edge-g edge-${data.highlight}`} data-testid={`connection-${id}`}>
      {halo && <path d={path} style={halo} className="proposal-halo" data-testid={`proposal-halo-${id}`} />}
      <BaseEdge id={id} path={path} markerEnd={markerEnd} style={style} interactionWidth={16} />
      {(data.showLabel || data.bundle > 1) && (
        <EdgeLabelRenderer>
          <div
            className={`${classes.join(' ')} nodrag nopan`}
            style={{ transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`, pointerEvents: data.bundle > 1 ? 'none' : 'all', cursor: 'pointer' }}
            data-testid={`connection-label-${id}`}
            onClick={(e) => {
              e.stopPropagation();
              if (data.bundle <= 1) selectEdge(id);
            }}
          >
            {data.bundle > 1 ? (
              `×${data.bundle}`
            ) : (
              <>
                {data.flowColor && !data.removed && data.step != null ? (
                  <span
                    className={`step-badge${data.before && data.before.step !== data.step ? ' step-changed' : ''}`}
                    style={{ background: FLOW_COLORS[data.flowColor] }}
                    data-testid={`connection-step-${id}`}
                    title={data.before && data.before.step !== data.step ? `Step ${data.before.step ?? '–'} → ${data.step}` : `Step ${data.step}`}
                  >
                    {data.step}
                  </span>
                ) : (
                  data.flowColor &&
                  !data.removed && <span className="flow-dot" style={{ background: FLOW_COLORS[data.flowColor] }} aria-hidden="true" />
                )}
                {data.orphaned && '⚠ '}
                {changedName && <span className="old-value">{data.before?.name}</span>}
                {changedName && ' → '}
                <span className="connection-name">{data.name}</span>
                {changedKind && (
                  <span className="kind-change">
                    {' '}
                    ({kindLabel(data.before?.kind)} → {kindLabel(data.kind)})
                  </span>
                )}
              </>
            )}
          </div>
        </EdgeLabelRenderer>
      )}
      {data.endpoint && data.bundle <= 1 && (
        <EdgeLabelRenderer>
          <EndpointBadge
            endpoint={data.endpoint}
            before={data.before?.endpoint && data.before.endpoint !== data.endpoint ? data.before.endpoint : null}
            missing={Boolean(data.endpointMissing)}
            compact={!data.showLabel}
            proposed={Boolean(data.proposed || (data.before && data.before.endpoint !== data.endpoint))}
            removed={Boolean(data.removed)}
            x={endpointX}
            y={endpointY}
            testId={`connection-endpoint-${id}`}
            onClick={() => selectEdge(id)}
          />
        </EdgeLabelRenderer>
      )}
    </g>
  );
}

/**
 * The target endpoint a connection uses (FR-054), drawn as its own badge near the arrowhead so it is visible
 * at a glance: the method badge plus the path, or only the method when zoomed far out. ⚠ marks an endpoint the
 * target's spec no longer has (FR-055).
 */
function EndpointBadge({
  endpoint,
  before,
  missing,
  compact,
  proposed,
  removed,
  x,
  y,
  testId,
  onClick,
}: {
  endpoint: string;
  before: string | null;
  missing: boolean;
  compact: boolean;
  proposed: boolean;
  removed: boolean;
  x: number;
  y: number;
  testId: string;
  onClick: () => void;
}) {
  const { method, path } = splitEndpoint(endpoint);
  const classes = ['endpoint-badge', 'nodrag', 'nopan'];
  if (missing) classes.push('missing');
  if (proposed) classes.push('proposed');
  if (removed) classes.push('removed');
  return (
    <div
      className={classes.join(' ')}
      style={{ transform: `translate(-50%, -50%) translate(${x}px, ${y}px)` }}
      data-testid={testId}
      title={missing ? `${endpoint}: not in the target's spec` : endpoint}
      onClick={(e) => {
        e.stopPropagation();
        onClick();
      }}
    >
      {missing && <span aria-label="not in the target's spec">⚠</span>}
      {method ? <MethodBadge method={method} /> : null}
      {!compact && (
        <span className="endpoint-path">
          {before && (
            <>
              <span className="old-value">{before}</span> →{' '}
            </>
          )}
          {path}
        </span>
      )}
    </div>
  );
}

export const ConnectionEdge = memo(ConnectionEdgeImpl);
