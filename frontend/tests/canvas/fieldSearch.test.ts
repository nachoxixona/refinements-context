import { describe, expect, it } from 'vitest';
import { fieldSearch } from '../../src/canvas/search/fieldSearch';
import { canvas, endpoint, field, view } from '../support/fixtures';

const g = view(
  canvas(
    [
      { id: 'orders', name: 'Orders', card: 'c1' },
      { id: 'billing', name: 'Billing', card: 'c2' },
      { id: 'empty', name: 'Empty' },
    ],
    [
      { id: 'c1', title: 'Orders API', endpoints: [endpoint('c1', 'GET', '/orders/{id}', [], [field('customerId', 'RESPONSE'), field('items[].productId', 'RESPONSE')])] },
      { id: 'c2', title: 'Billing API', endpoints: [endpoint('c2', 'POST', '/invoices', [field('customer_id', 'BODY')])] },
      { id: 'c3', title: 'Loose API', endpoints: [{ ...endpoint('c3', 'GET', '/customers'), operationId: 'listCustomers' }] },
    ],
  ),
);

describe('fieldSearch (FR-030, FR-031)', () => {
  it('matches fields case-insensitively and partially, with the linked microservice or unlinked', () => {
    const r = fieldSearch(g.nodes, 'CUSTOMERID', 'field');
    expect(r.hits.filter((h) => h.fieldPath).map((h) => [h.nodeId, h.fieldPath, h.ownerName])).toEqual([
      ['card:c1', 'customerId', 'Orders'],
      ['card:c2', 'customer_id', 'Billing'],
    ]);
    expect([...r.owners].sort()).toEqual(['ms:billing', 'ms:orders']);
  });

  it('matches nested paths, endpoint paths and operation ids', () => {
    expect(fieldSearch(g.nodes, 'productid', 'field').hits[0]!.fieldPath).toBe('items[].productId');
    const op = fieldSearch(g.nodes, 'listcust', 'field');
    expect(op.hits.map((h) => [h.nodeId, h.ownerName])).toEqual([['card:c3', null]]);
    expect(op.byNode.has('card:c3')).toBe(true);
    expect(op.owners.size).toBe(0);
  });

  it('name mode finds microservices and card titles', () => {
    const r = fieldSearch(g.nodes, 'bill', 'name');
    expect(r.hits.map((h) => h.nodeId)).toEqual(['ms:billing', 'card:c2']);
  });

  it('is fast on 100 cards of 20 endpoints (SC-006)', () => {
    const cards = Array.from({ length: 100 }, (_, i) => ({
      id: `c${i}`,
      endpoints: Array.from({ length: 20 }, (_, j) =>
        endpoint(`c${i}`, 'GET', `/r${j}`, [field(`in${j}Id`, 'QUERY')], [field(`out${j}Name`, 'RESPONSE'), field(`nested.v${j}`, 'RESPONSE')]),
      ),
    }));
    const big = view(canvas([], cards));
    const t0 = performance.now();
    const r = fieldSearch(big.nodes, 'name', 'field');
    expect(performance.now() - t0).toBeLessThan(1000);
    expect(r.hits.length).toBe(2000);
  });

  it('returns nothing for an empty query', () => {
    expect(fieldSearch(g.nodes, '  ', 'field').hits).toEqual([]);
  });
});
