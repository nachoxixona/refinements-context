import { describe, expect, it } from 'vitest';
import type { ViewEdge } from '../../src/canvas/viewModel';
import { flowSteps, reorderSteps } from '../../src/walkthrough/steps';

const edge = (id: string, source: string, target: string, step: number | null, flowId: string | null = 'f', removed = false): ViewEdge => ({
  id,
  source,
  target,
  data: { connectionId: id, name: id, kind: null, description: null, flowId, endpoint: null, step, removed: removed || undefined },
});

describe('flow steps (spec 004 R2)', () => {
  const edges = [
    edge('c', 'ms:b', 'ms:c', 2),
    edge('a', 'ms:a', 'ms:b', 1),
    edge('b', 'ms:b', 'ms:d', 2),
    edge('z', 'ms:c', 'ms:a', null),
    edge('y', 'ms:d', 'ms:a', null),
    edge('other', 'ms:a', 'ms:c', 1, 'g'),
    edge('gone', 'ms:a', 'ms:d', 3, 'f', true),
  ];

  it('orders numbered steps, groups parallel ones, and appends unnumbered ones by name', () => {
    const steps = flowSteps(edges, 'f');
    expect(steps.map((s) => s.edgeIds)).toEqual([['a'], ['b', 'c'], ['y'], ['z']]);
    expect(steps[1]!.nodeIds).toEqual(['ms:b', 'ms:d', 'ms:c']);
    expect(steps.map((s) => s.step)).toEqual([1, 2, null, null]);
  });

  it('leaves out other flows and proposed removals', () => {
    expect(flowSteps(edges, 'g').map((s) => s.edgeIds)).toEqual([['other']]);
    expect(flowSteps(edges, 'f').flatMap((s) => s.edgeIds)).not.toContain('gone');
  });

  it('reorders by swapping step groups and renumbers 1..n, keeping parallel steps together', () => {
    const steps = flowSteps(edges, 'f');
    expect(reorderSteps(steps, 1, -1, (id) => id)).toEqual([
      { connectionId: 'b', step: 1 },
      { connectionId: 'c', step: 1 },
      { connectionId: 'a', step: 2 },
      { connectionId: 'y', step: 3 },
      { connectionId: 'z', step: 4 },
    ]);
    expect(reorderSteps(steps, 0, -1, (id) => id)[0]).toEqual({ connectionId: 'a', step: 1 });
  });
});
