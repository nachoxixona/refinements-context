import { describe, expect, it } from 'vitest';
import { blastRadius } from '../../src/canvas/highlight/blastRadius';
import { edgeHighlight, nodeHighlight } from '../../src/canvas/highlight/highlightState';
import { canvas, view } from '../support/fixtures';

const g = view(
  canvas(
    [{ id: 'a', card: 'ca' }, { id: 'b' }, { id: 'c' }, { id: 'd' }],
    [{ id: 'ca' }, { id: 'loose' }],
    [
      { id: '1', source: 'a', target: 'b' },
      { id: '2', source: 'a', target: 'b' },
      { id: '3', source: 'c', target: 'a' },
      { id: '4', source: 'b', target: 'd' },
    ],
  ),
);

describe('flow highlight (FR-051)', () => {
  it('lights the flow connections and the microservices they connect; docked cards follow', () => {
    const input = {
      mode: 'flow' as const,
      flowId: 'g1',
      edgeIds: new Set(['connection:1']),
      nodeIds: new Set(['ms:a', 'ms:b']),
    };
    const hl = (id: string) => nodeHighlight(g.nodes.find((n) => n.id === id)!, input);
    expect(hl('ms:a')).toBe('search');
    expect(hl('ms:b')).toBe('search');
    expect(hl('ms:c')).toBe('dimmed');
    expect(hl('card:ca')).toBe('none');
    expect(hl('card:loose')).toBe('dimmed');
    expect(edgeHighlight({ id: 'connection:1', source: 'ms:a', target: 'ms:b' }, input)).toBe('active');
    expect(edgeHighlight({ id: 'connection:2', source: 'ms:a', target: 'ms:b' }, input)).toBe('dimmed');
  });
});

describe('blastRadius (FR-033)', () => {
  it('one hop over connections; several connections per pair count once per neighbour', () => {
    const r = blastRadius('ms:a', g.edges);
    expect([...r.upstream]).toEqual(['ms:c']);
    expect([...r.downstream]).toEqual(['ms:b']);
    expect([...r.edgeIds].sort()).toEqual(['connection:1', 'connection:2', 'connection:3']);
  });

  it('ignores connections proposed for removal', () => {
    const edges = g.edges.map((e) => (e.id === 'connection:3' ? { ...e, data: { ...e.data, removed: true } } : e));
    expect(blastRadius('ms:a', edges).upstream.size).toBe(0);
  });

  it('docked cards follow their microservice; unrelated nodes are dimmed', () => {
    const input = { mode: 'blast' as const, selectedId: 'ms:a', blast: blastRadius('ms:a', g.edges) };
    const hl = (id: string) => nodeHighlight(g.nodes.find((n) => n.id === id)!, input);
    expect(hl('ms:a')).toBe('selected');
    expect(hl('card:ca')).toBe('both');
    expect(hl('ms:b')).toBe('downstream');
    expect(hl('ms:c')).toBe('upstream');
    expect(hl('ms:d')).toBe('dimmed');
    expect(hl('card:loose')).toBe('dimmed');
  });
});
