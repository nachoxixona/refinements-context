import type { ZoomTier } from '../zoomTier';
import { pairKey, type PairEdge } from './parallelOffsets';

/** A bundle of `count` connections between the same pair, drawn as one edge when zoomed far out. */
export interface Bundled<E extends PairEdge> {
  edge: E;
  count: number;
  members: E[];
}

/**
 * Below the overview tier, several connections between the same pair are drawn as one edge labelled `×n`
 * (research R11); otherwise every connection is kept. Single connections are never bundled.
 */
export function bundle<E extends PairEdge>(edges: readonly E[], tier: ZoomTier): Bundled<E>[] {
  if (tier !== 'low') return edges.map((edge) => ({ edge, count: 1, members: [edge] }));
  const flows = new Map<string, E[]>();
  for (const e of edges) {
    const k = pairKey(e.source, e.target);
    const g = flows.get(k);
    if (g) g.push(e);
    else flows.set(k, [e]);
  }
  const out: Bundled<E>[] = [];
  for (const [k, members] of flows) {
    const first = members[0]!;
    out.push({
      edge: members.length === 1 ? first : { ...first, id: `bundle:${k}` },
      count: members.length,
      members,
    });
  }
  return out;
}
