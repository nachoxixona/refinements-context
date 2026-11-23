import type { ViewEdge } from '../canvas/viewModel';

/** One step of a flow walkthrough: its connections happen in parallel (spec 004 FR-001, research R2). */
export interface WalkStep {
  /** the step number, or null for an unnumbered connection (each is a step of its own) */
  step: number | null;
  edgeIds: string[];
  nodeIds: string[];
}

const byName = (a: ViewEdge, b: ViewEdge) => a.data.name.localeCompare(b.data.name) || a.id.localeCompare(b.id);

/**
 * The ordered steps of a flow over the current view (baseline, or baseline ⊕ draft): numbered steps in ascending
 * order, connections sharing a number together; unnumbered connections follow, one step each, in name order.
 * Connections proposed for removal are left out.
 */
export function flowSteps(edges: readonly ViewEdge[], flowId: string): WalkStep[] {
  const members = edges.filter((e) => e.data.flowId === flowId && !e.data.removed);
  const numbered = new Map<number, ViewEdge[]>();
  const loose: ViewEdge[] = [];
  for (const e of members) {
    const s = e.data.step;
    if (typeof s === 'number' && s >= 1) numbered.set(s, [...(numbered.get(s) ?? []), e]);
    else loose.push(e);
  }
  const toStep = (step: number | null, list: ViewEdge[]): WalkStep => {
    const sorted = [...list].sort(byName);
    const nodeIds: string[] = [];
    for (const e of sorted) for (const id of [e.source, e.target]) if (!nodeIds.includes(id)) nodeIds.push(id);
    return { step, edgeIds: sorted.map((e) => e.id), nodeIds };
  };
  return [
    ...[...numbered.keys()].sort((a, b) => a - b).map((s) => toStep(s, numbered.get(s)!)),
    ...loose.sort(byName).map((e) => toStep(null, [e])),
  ];
}

/**
 * Steps after moving the step group at `index` by `delta` (−1 up, +1 down), renumbered 1..n with parallel groups kept
 * together (FR-004). Returns connectionId → step for every connection of the flow.
 */
export function reorderSteps(steps: readonly WalkStep[], index: number, delta: -1 | 1, connectionIdOf: (edgeId: string) => string) {
  const order = [...steps];
  const to = index + delta;
  if (to >= 0 && to < order.length) [order[index], order[to]] = [order[to]!, order[index]!];
  const out: { connectionId: string; step: number }[] = [];
  order.forEach((s, i) => s.edgeIds.forEach((id) => out.push({ connectionId: connectionIdOf(id), step: i + 1 })));
  return out;
}
