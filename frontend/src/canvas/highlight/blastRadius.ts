export interface BlastEdge {
  id: string;
  source: string;
  target: string;
  data?: { removed?: boolean };
}

export interface BlastRadius {
  upstream: Set<string>;
  downstream: Set<string>;
  edgeIds: Set<string>;
}

/**
 * One hop only (FR-033): upstream = sources of connections into the selection, downstream = targets of connections out
 * of it. Several connections per pair count once per neighbour; connections proposed for removal are ignored.
 */
export function blastRadius(selectedId: string, edges: readonly BlastEdge[]): BlastRadius {
  const upstream = new Set<string>();
  const downstream = new Set<string>();
  const edgeIds = new Set<string>();
  for (const e of edges) {
    if (e.source === e.target || e.data?.removed) continue;
    if (e.target === selectedId) {
      upstream.add(e.source);
      edgeIds.add(e.id);
    }
    if (e.source === selectedId) {
      downstream.add(e.target);
      edgeIds.add(e.id);
    }
  }
  return { upstream, downstream, edgeIds };
}
