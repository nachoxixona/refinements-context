import type { ELK, ElkNode } from 'elkjs/lib/elk-api';
import type { XYPosition } from '../../api/types';

export interface LayoutNode {
  id: string;
  position: XYPosition | null;
  /** Size of the whole block: a microservice and its docked card are laid out as one (research R8). */
  width?: number;
  height?: number;
  /** `cards` puts unlinked spec cards in a final row below the microservices. */
  row?: 'cards';
}

export interface LayoutEdge {
  id: string;
  source: string;
  target: string;
}

export type Positions = Record<string, XYPosition>;

const DEFAULT_WIDTH = 300;
const DEFAULT_HEIGHT = 160;
const COLUMN_GAP = 120;
const ROW_GAP = 40;

const LAYOUT_OPTIONS = {
  'elk.algorithm': 'layered',
  'elk.direction': 'RIGHT',
  'elk.spacing.nodeNode': '60',
  'elk.layered.spacing.nodeNodeBetweenLayers': '140',
  'elk.edgeRouting': 'SPLINES',
};

let elkPromise: Promise<ELK> | null = null;

/** ELK in a Web Worker in the browser; in-thread (bundled build) where workers are unavailable (tests). */
function getElk(): Promise<ELK> {
  if (!elkPromise) {
    elkPromise = (async () => {
      if (typeof Worker !== 'undefined' && import.meta.env.MODE !== 'test') {
        const [{ default: ElkApi }, { elkWorkerUrl }] = await Promise.all([
          import('elkjs/lib/elk-api'),
          import('./elk.worker'),
        ]);
        return new ElkApi({ workerUrl: elkWorkerUrl });
      }
      const { default: ElkBundled } = await import('elkjs/lib/elk.bundled.js');
      return new ElkBundled();
    })();
  }
  return elkPromise;
}

/**
 * Explicit Auto-arrange (FR-014, Principle III): hierarchical ELK layout of every node, using measured
 * sizes. Returns positions only; saving them is the caller's decision after the user confirms.
 */
export async function autoArrange(allNodes: readonly LayoutNode[], edges: readonly LayoutEdge[]): Promise<Positions> {
  if (allNodes.length === 0) return {};
  const nodes = allNodes.filter((n) => n.row !== 'cards');
  const cards = allNodes.filter((n) => n.row === 'cards');
  const ids = new Set(nodes.map((n) => n.id));
  const graph: ElkNode = {
    id: 'root',
    layoutOptions: LAYOUT_OPTIONS,
    children: nodes.map((n) => ({ id: n.id, width: n.width ?? DEFAULT_WIDTH, height: n.height ?? DEFAULT_HEIGHT })),
    edges: edges
      .filter((e) => ids.has(e.source) && ids.has(e.target) && e.source !== e.target)
      .map((e) => ({ id: e.id, sources: [e.source], targets: [e.target] })),
  };
  const positions: Positions = {};
  let bottom = 0;
  if (nodes.length > 0) {
    const elk = await getElk();
    const result = await elk.layout(graph);
    for (const child of result.children ?? []) {
      positions[child.id] = { x: Math.round(child.x ?? 0), y: Math.round(child.y ?? 0) };
      bottom = Math.max(bottom, Math.round((child.y ?? 0) + (child.height ?? DEFAULT_HEIGHT)));
    }
  }
  let x = 0;
  const y = nodes.length > 0 ? bottom + COLUMN_GAP : 0;
  for (const c of cards) {
    positions[c.id] = { x, y };
    x += (c.width ?? DEFAULT_WIDTH) + ROW_GAP;
  }
  return positions;
}

/**
 * Positions for nodes that have none (research R11): a full ELK layout on the first load (no node has a
 * position); otherwise a column right of the current bounding box, leaving existing positions untouched.
 */
export async function placeUnpositioned(
  nodes: readonly LayoutNode[],
  edges: readonly LayoutEdge[],
): Promise<{ positions: Positions; ranElk: boolean }> {
  const unplaced = nodes.filter((n) => n.position === null);
  if (unplaced.length === 0) return { positions: {}, ranElk: false };
  if (unplaced.length === nodes.length) {
    return { positions: await autoArrange(nodes, edges), ranElk: true };
  }
  return { positions: columnRightOf(nodes), ranElk: false };
}

/** Synchronous column placement, also used for proposed services in draft mode. */
export function columnRightOf(nodes: readonly LayoutNode[]): Positions {
  const placed = nodes.filter((n): n is LayoutNode & { position: XYPosition } => n.position !== null);
  let maxX = 0;
  let minY = 0;
  if (placed.length > 0) {
    maxX = Math.max(...placed.map((n) => n.position.x + (n.width ?? DEFAULT_WIDTH)));
    minY = Math.min(...placed.map((n) => n.position.y));
  }
  const x = placed.length > 0 ? maxX + COLUMN_GAP : 0;
  let y = minY;
  const positions: Positions = {};
  for (const n of nodes) {
    if (n.position !== null) continue;
    positions[n.id] = { x, y };
    y += (n.height ?? DEFAULT_HEIGHT) + ROW_GAP;
  }
  return positions;
}
