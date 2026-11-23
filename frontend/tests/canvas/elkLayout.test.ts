import { afterEach, describe, expect, it, vi } from 'vitest';
import { autoArrange, placeUnpositioned } from '../../src/canvas/layout/elkLayout';

const edges = [
  { id: 'a-b', source: 'a', target: 'b' },
  { id: 'b-c', source: 'b', target: 'c' },
];

afterEach(() => vi.restoreAllMocks());

describe('elkLayout', () => {
  it('applies a full ELK layout when no node has a position', async () => {
    const { positions, ranElk } = await placeUnpositioned(
      [
        { id: 'a', position: null },
        { id: 'b', position: null },
        { id: 'c', position: null },
      ],
      edges,
    );
    expect(ranElk).toBe(true);
    expect(Object.keys(positions).sort()).toEqual(['a', 'b', 'c']);
    // layered RIGHT: a is left of b, b left of c
    expect(positions.a!.x).toBeLessThan(positions.b!.x);
    expect(positions.b!.x).toBeLessThan(positions.c!.x);
  });

  it('puts new nodes in a column right of the bounding box and leaves existing ones alone', async () => {
    const { positions, ranElk } = await placeUnpositioned(
      [
        { id: 'a', position: { x: 0, y: 50 }, width: 200, height: 100 },
        { id: 'b', position: { x: 400, y: 0 }, width: 300, height: 100 },
        { id: 'n1', position: null, height: 80 },
        { id: 'n2', position: null, height: 80 },
      ],
      edges,
    );
    expect(ranElk).toBe(false);
    expect(Object.keys(positions).sort()).toEqual(['n1', 'n2']);
    expect(positions.n1!.x).toBeGreaterThan(700);
    expect(positions.n2!.x).toBe(positions.n1!.x);
    expect(positions.n1!.y).toBe(0);
    expect(positions.n2!.y).toBeGreaterThan(positions.n1!.y);
  });

  it('puts unlinked cards in a final row below the microservice blocks (research R8)', async () => {
    const positions = await autoArrange(
      [
        { id: 'a', position: null, width: 200, height: 300 },
        { id: 'b', position: null, width: 200, height: 100 },
        { id: 'card1', position: null, width: 250, row: 'cards' },
        { id: 'card2', position: null, width: 250, row: 'cards' },
      ],
      [{ id: 'a-b', source: 'a', target: 'b' }],
    );
    const lowest = Math.max(positions.a!.y + 300, positions.b!.y + 100);
    expect(positions.card1!.y).toBeGreaterThan(lowest);
    expect(positions.card2!.y).toBe(positions.card1!.y);
    expect(positions.card2!.x).toBeGreaterThan(positions.card1!.x + 249);
  });

  it('autoArrange returns positions for every node and never saves by itself', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    const positions = await autoArrange(
      [
        { id: 'a', position: { x: 900, y: 900 } },
        { id: 'b', position: { x: 0, y: 0 } },
        { id: 'c', position: null },
      ],
      edges,
    );
    expect(Object.keys(positions).sort()).toEqual(['a', 'b', 'c']);
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});
