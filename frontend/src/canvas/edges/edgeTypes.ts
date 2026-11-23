import type { Edge } from '@xyflow/react';
import type { FlowColor } from '../../api/types';
import type { ViewEdgeData } from '../viewModel';

export type EdgeHighlight = 'none' | 'active' | 'dimmed';

export type ConnectionEdgeUiData = ViewEdgeData & {
  highlight: EdgeHighlight;
  /** perpendicular offset for parallel connections (research R11) */
  offset: number;
  /** > 1 when this edge stands for a bundle of connections */
  bundle: number;
  showLabel: boolean;
  /** color of the connection's flow, if any (FR-050) */
  flowColor: FlowColor | null;
};

export type ConnectionRfEdge = Edge<ConnectionEdgeUiData, 'connection'>;
