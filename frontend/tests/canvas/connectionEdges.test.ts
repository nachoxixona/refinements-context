import { describe, expect, it } from 'vitest';
import { bundle } from '../../src/canvas/edges/bundle';
import { connectionPath, splitEndpoint } from '../../src/canvas/edges/ConnectionEdge';
import { CONNECTION_COLORS, FLOW_COLORS, connectionStyle, proposalHalo } from '../../src/canvas/edges/connectionStyle';
import { PARALLEL_GAP, parallelOffsets } from '../../src/canvas/edges/parallelOffsets';
import { endpointMatchKey } from '../../src/canvas/viewModel';

const e = (id: string, source: string, target: string) => ({ id, source, target });

describe('parallelOffsets (research R11)', () => {
  it('a single connection is straight', () => {
    expect(parallelOffsets([e('a', 'x', 'y')]).get('a')).toBe(0);
  });

  it('n connections per pair get symmetric, distinct offsets', () => {
    const o = parallelOffsets([e('a', 'x', 'y'), e('b', 'x', 'y'), e('c', 'x', 'y')]);
    expect([o.get('a'), o.get('b'), o.get('c')]).toEqual([-PARALLEL_GAP, 0, PARALLEL_GAP]);
  });

  it('opposite directions share the flow, so their curves never overlap', () => {
    const o = parallelOffsets([e('a', 'x', 'y'), e('b', 'y', 'x')]);
    const pa = connectionPath(0, 0, 100, 0, o.get('a')!); // x → y
    const pb = connectionPath(100, 0, 0, 0, o.get('b')!); // y → x
    expect(pa.labelY).not.toBeCloseTo(pb.labelY);
    expect(Math.abs(pa.labelY - pb.labelY)).toBeCloseTo(PARALLEL_GAP);
  });
});

describe('connectionStyle (FR-018b)', () => {
  it('request solid, event dashed, no kind dotted and neutral', () => {
    expect(connectionStyle('REQUEST', {}, 2).strokeDasharray).toBeUndefined();
    expect(connectionStyle('EVENT', {}, 2).strokeDasharray).toBe('6 4');
    const none = connectionStyle(null, {}, 2);
    expect(none.strokeDasharray).toBe('2 4');
    expect(none.stroke).toBe(CONNECTION_COLORS.neutral);
  });

  it('proposed connections keep their kind line and get the proposed color; removals are faded red', () => {
    const p = connectionStyle('EVENT', { proposed: true }, 2);
    expect(p.strokeDasharray).toBe('6 4');
    expect(p.stroke).toBe(CONNECTION_COLORS.proposed);
    const r = connectionStyle('REQUEST', { removed: true }, 2);
    expect(r.stroke).toBe(CONNECTION_COLORS.removed);
    expect(r.opacity).toBeLessThan(1);
  });
});

describe('flow colors (FR-050)', () => {
  it('a grouped connection takes its flow color and keeps its kind line style', () => {
    const s = connectionStyle('EVENT', {}, 2, 'ORANGE');
    expect(s.stroke).toBe(FLOW_COLORS.ORANGE);
    expect(s.strokeDasharray).toBe('6 4');
    expect(connectionStyle(null, {}, 2, 'BLUE').stroke).toBe(FLOW_COLORS.BLUE);
  });

  it('a proposed connection in a flow keeps the flow color and gets a green halo (regression)', () => {
    expect(connectionStyle('REQUEST', { proposed: true }, 2, 'BLUE').stroke).toBe(FLOW_COLORS.BLUE);
    expect(proposalHalo({ proposed: true }, 'BLUE', 2)?.stroke).toBe(CONNECTION_COLORS.proposed);
    expect(proposalHalo({}, 'BLUE', 2)).toBeNull();
    expect(proposalHalo({ proposed: true }, null, 2)).toBeNull();
    expect(connectionStyle('REQUEST', { proposed: true }, 2, null).stroke).toBe(CONNECTION_COLORS.proposed);
  });

  it('removals stay red, and the palette has no green or red', () => {
    expect(connectionStyle('REQUEST', { removed: true }, 2, 'BLUE').stroke).toBe(CONNECTION_COLORS.removed);
    expect(proposalHalo({ proposed: true, removed: true }, 'BLUE', 2)).toBeNull();
    expect(Object.values(FLOW_COLORS)).not.toContain(CONNECTION_COLORS.proposed);
    expect(Object.values(FLOW_COLORS)).not.toContain(CONNECTION_COLORS.removed);
  });
});

describe('endpoint badge placement', () => {
  it('sits on the curve near the target end and splits method and path', () => {
    const p = connectionPath(0, 0, 100, 0, 0);
    expect(p.endpointX).toBeCloseTo(78);
    expect(p.endpointY).toBeCloseTo(0);
    const curved = connectionPath(0, 0, 100, 0, 18);
    expect(curved.endpointY).toBeGreaterThan(0);
    expect(splitEndpoint('POST /invoices/{id}')).toEqual({ method: 'POST', path: '/invoices/{id}' });
    expect(splitEndpoint('weird')).toEqual({ method: null, path: 'weird' });
  });
});

describe('endpointMatchKey', () => {
  it('compares like the backend: method upper-cased, path templates normalized', () => {
    expect(endpointMatchKey('get /orders/{id}')).toBe(endpointMatchKey('GET /orders/{orderId}'));
    expect(endpointMatchKey('POST /a')).not.toBe(endpointMatchKey('GET /a'));
  });
});

describe('bundle', () => {
  const edges = [e('a', 'x', 'y'), e('b', 'y', 'x'), e('c', 'x', 'y'), e('d', 'x', 'z')];

  it('keeps every connection at overview zoom and above', () => {
    expect(bundle(edges, 'mid')).toHaveLength(4);
    expect(bundle(edges, 'high').every((b) => b.count === 1)).toBe(true);
  });

  it('bundles several connections per pair into one ×n edge when zoomed far out', () => {
    const b = bundle(edges, 'low');
    expect(b).toHaveLength(2);
    const xy = b.find((x) => x.count === 3)!;
    expect(xy.edge.id).toBe('bundle:x|y');
    expect(xy.members.map((m) => m.id)).toEqual(['a', 'b', 'c']);
    expect(b.find((x) => x.count === 1)!.edge.id).toBe('d');
  });
});
