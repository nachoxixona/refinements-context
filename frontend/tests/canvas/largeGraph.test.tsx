import { describe, expect, it } from 'vitest';
import { bundle } from '../../src/canvas/edges/bundle';
import { parallelOffsets } from '../../src/canvas/edges/parallelOffsets';
import { blastRadius } from '../../src/canvas/highlight/blastRadius';
import { NodeDataCache, nodeHighlight, type HighlightInput } from '../../src/canvas/highlight/highlightState';
import { canvas, endpoint, view } from '../support/fixtures';

// 100 microservices, 100 docked cards, 500 connections (the plan's scale)
const ids = Array.from({ length: 100 }, (_, i) => `m${i}`);
const connections = Array.from({ length: 500 }, (_, i) => {
  const s = i % 100;
  let t = (s + 1 + Math.floor(i / 100)) % 100;
  if (t === s) t = (t + 1) % 100;
  return { id: `f${i}`, source: ids[s]!, target: ids[t]!, name: `Connection ${i}` };
});
const g = view(
  canvas(
    ids.map((id) => ({ id, card: `c-${id}` })),
    ids.map((id) => ({ id: `c-${id}`, endpoints: [endpoint(`c-${id}`, 'GET', '/x')] })),
    connections,
  ),
);

function build(cache: NodeDataCache, input: HighlightInput) {
  return g.nodes.map((n) => cache.get(n, nodeHighlight(n, input), undefined, false));
}

const blast = (id: string): HighlightInput => ({ mode: 'blast', selectedId: id, blast: blastRadius(id, g.edges) });

describe('large canvas (100 microservices, 100 cards, 500 connections)', () => {
  it('a selection change re-creates data only for nodes whose highlight changed', () => {
    const cache = new NodeDataCache();
    const first = build(cache, blast('ms:m0'));
    const t0 = performance.now();
    const second = build(cache, blast('ms:m50'));
    const elapsed = performance.now() - t0;
    const changed = second.filter((d, i) => d !== first[i]);
    expect(changed.length).toBeGreaterThan(0);
    expect(changed.length).toBeLessThan(g.nodes.length);
    expect(elapsed).toBeLessThan(50);
  });

  it('re-running the same highlight reuses every data object (no re-render)', () => {
    const cache = new NodeDataCache();
    const a = build(cache, blast('ms:m3'));
    const b = build(cache, blast('ms:m3'));
    b.forEach((d, i) => expect(d).toBe(a[i]));
  });

  it('offsets and bundling handle 500 connections quickly and without overlap within a pair', () => {
    const t0 = performance.now();
    const offsets = parallelOffsets(g.edges);
    const bundled = bundle(g.edges, 'low');
    expect(performance.now() - t0).toBeLessThan(50);
    expect(offsets.size).toBe(500);
    expect(bundled.reduce((n, b) => n + b.count, 0)).toBe(500);
    const byPair = new Map<string, number[]>();
    for (const e of g.edges) {
      const k = [e.source, e.target].sort().join('|');
      const canonical = e.source < e.target ? offsets.get(e.id)! : -offsets.get(e.id)!;
      byPair.set(k, [...(byPair.get(k) ?? []), canonical]);
    }
    for (const list of byPair.values()) expect(new Set(list).size).toBe(list.length);
  });
});
