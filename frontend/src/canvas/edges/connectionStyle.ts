import type { CSSProperties } from 'react';
import type { ConnectionKind, FlowColor } from '../../api/types';
import type { ProposalMarks } from '../viewModel';

/**
 * Connection strokes as inline styles with literal colors (research R11). Inline styles survive html-to-image
 * cloning, so exported PNGs keep their arrows. Line style encodes the kind (FR-018b): request solid, event
 * dashed, no kind dotted; color encodes baseline / proposed / removal / orphan.
 */
export const CONNECTION_COLORS = {
  baseline: '#1f2937',
  neutral: '#8a94a6',
  proposed: '#16a34a',
  removed: '#dc2626',
  orphaned: '#d97706',
} as const;

/** The flow palette (FR-049): no green or red, which mark proposed and removed elements. */
export const FLOW_COLORS: Record<FlowColor, string> = {
  BLUE: '#2563eb',
  PURPLE: '#7c3aed',
  ORANGE: '#ea580c',
  PINK: '#db2777',
  AMBER: '#ca8a04',
  BROWN: '#92400e',
  CYAN: '#0891b2',
  SLATE: '#475569',
};

export const KIND_DASH: Record<'REQUEST' | 'EVENT' | 'NONE', string | undefined> = {
  REQUEST: undefined,
  EVENT: '6 4',
  NONE: '2 4',
};

/**
 * Removal and orphan colors win in draft mode. A connection in a flow keeps the flow's color (FR-050), even when
 * proposed: it is then marked by a green halo instead (see {@link proposalHalo}, Principle IV). A proposed
 * connection without a flow is green.
 */
export function connectionColor(kind: ConnectionKind | null, marks: ProposalMarks = {}, flow?: FlowColor | null): string {
  if (marks.removed) return CONNECTION_COLORS.removed;
  if (marks.orphaned) return CONNECTION_COLORS.orphaned;
  if (flow) return FLOW_COLORS[flow];
  if (marks.proposed) return CONNECTION_COLORS.proposed;
  return kind === null ? CONNECTION_COLORS.neutral : CONNECTION_COLORS.baseline;
}

export function connectionStyle(kind: ConnectionKind | null, marks: ProposalMarks, width: number, flow?: FlowColor | null): CSSProperties {
  const style: CSSProperties = { stroke: connectionColor(kind, marks, flow), strokeWidth: width };
  const dash = KIND_DASH[kind ?? 'NONE'];
  if (dash) style.strokeDasharray = dash;
  if (marks.removed) style.opacity = 0.45;
  return style;
}

/** The green halo drawn under a proposed connection that keeps its flow's color; null when none is needed. */
export function proposalHalo(marks: ProposalMarks, flow: FlowColor | null | undefined, width: number): CSSProperties | null {
  if (!marks.proposed || !flow || marks.removed || marks.orphaned) return null;
  return { stroke: CONNECTION_COLORS.proposed, strokeWidth: width + 6, strokeOpacity: 0.35, fill: 'none', strokeLinecap: 'round' };
}

/** Display text for a kind. */
export const kindLabel = (kind: ConnectionKind | null | undefined) => (kind === 'REQUEST' ? 'request' : kind === 'EVENT' ? 'event' : 'no kind');
