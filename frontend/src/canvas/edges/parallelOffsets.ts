/** Distance between neighbouring parallel connections, in pixels (research R11). */
export const PARALLEL_GAP = 18;

export interface PairEdge {
  id: string;
  source: string;
  target: string;
}

/** Unordered pair key, so A→B and B→A share a flow. */
export const pairKey = (a: string, b: string) => (a < b ? `${a}|${b}` : `${b}|${a}`);

/**
 * Perpendicular offset for every edge, so several connections between the same two microservices never overlap
 * (FR-018). Offsets are symmetric around the straight line in the pair's canonical direction (smaller id →
 * larger id) and then expressed in each edge's own direction, which flips the sign for reversed edges.
 */
export function parallelOffsets(edges: readonly PairEdge[]): Map<string, number> {
  const flows = new Map<string, PairEdge[]>();
  for (const e of edges) {
    const k = pairKey(e.source, e.target);
    const g = flows.get(k);
    if (g) g.push(e);
    else flows.set(k, [e]);
  }
  const out = new Map<string, number>();
  for (const flow of flows.values()) {
    const sorted = [...flow].sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
    const n = sorted.length;
    sorted.forEach((e, i) => {
      const canonical = (i - (n - 1) / 2) * PARALLEL_GAP;
      out.set(e.id, e.source < e.target ? canonical : -canonical);
    });
  }
  return out;
}
