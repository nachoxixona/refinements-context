import { describe, expect, it } from 'vitest';
import type { ScenarioContent } from '../../src/api/types';
import { applyOverlay } from '../../src/scenario/overlay';
import { emptyDraft } from '../../src/scenario/scenarioStore';
import { isCard, isMicroservice, type CardView, type MicroserviceView } from '../../src/canvas/viewModel';
import { canvas, deepFreeze, endpoint, field, view } from '../support/fixtures';

const baseline = deepFreeze(
  view(
    canvas(
      [
        { id: 'orders', name: 'Orders', card: 'c1', position: { x: 0, y: 0 } },
        { id: 'billing', name: 'Billing', position: { x: 400, y: 0 } },
      ],
      [
        { id: 'c1', title: 'Orders API', endpoints: [endpoint('c1', 'GET', '/orders/{orderId}', [], [field('items', 'RESPONSE', 'array'), field('items[].productId', 'RESPONSE'), field('total', 'RESPONSE', 'number')])] },
        { id: 'c2', title: 'Loose API', position: { x: 0, y: 400 } },
      ],
      [
        { id: 'f1', source: 'orders', target: 'billing', name: 'Create invoice', kind: 'REQUEST' },
        { id: 'f2', source: 'billing', target: 'orders', name: 'Invoice paid', kind: 'EVENT' },
      ],
    ),
  ),
);

const draft = (over: Partial<ScenarioContent>): ScenarioContent => ({ ...emptyDraft(), ...over });
const ms = (v: ReturnType<typeof applyOverlay>, id: string) => v.nodes.find((n) => n.id === id)!.data as MicroserviceView;
const card = (v: ReturnType<typeof applyOverlay>, id: string) => v.nodes.find((n) => n.id === id)!;

describe('applyOverlay (content v2)', () => {
  it('returns the baseline itself for an empty draft and never mutates it', () => {
    expect(applyOverlay(baseline, emptyDraft())).toBe(baseline);
    const before = JSON.stringify(baseline);
    applyOverlay(
      baseline,
      draft({
        proposedMicroservices: [{ tempKey: 'proposed:loyalty', name: 'Loyalty', card: { title: 'Loyalty API', endpoints: [{ method: 'POST', path: '/points' }] } }],
        proposedConnections: [{ id: 'pf1', source: 'orders', target: 'proposed:loyalty', name: 'Points earned', kind: 'EVENT' }],
        removals: { connections: ['f1'], microservices: ['billing'], links: [] },
        connectionChanges: [{ connectionId: 'f2', kind: 'REQUEST' }],
      }),
    );
    expect(JSON.stringify(baseline)).toBe(before);
  });

  it('adds proposed microservices with a docked proposed card, and proposed connections', () => {
    const v = applyOverlay(
      baseline,
      draft({
        proposedMicroservices: [{ tempKey: 'proposed:loyalty', name: 'Loyalty', card: { title: 'Loyalty API', endpoints: [{ method: 'POST', path: '/points', inputs: [{ name: 'customerId' }] }] } }],
        proposedConnections: [{ id: 'pf1', source: 'orders', target: 'proposed:loyalty', name: 'Points earned', kind: 'EVENT' }],
      }),
    );
    expect(ms(v, 'proposed:loyalty').proposed).toBe(true);
    const c = card(v, 'card:proposed:loyalty');
    expect(c.parentId).toBe('proposed:loyalty');
    expect((c.data as CardView).endpoints[0]!.endpointKey).toBe('proposed:loyalty:POST /points');
    const e = v.edges.find((x) => x.id === 'proposed:pf1')!;
    expect([e.source, e.target, e.data.proposed, e.data.kind]).toEqual(['ms:orders', 'proposed:loyalty', true, 'EVENT']);
    // parents before children
    const idx = (id: string) => v.nodes.findIndex((n) => n.id === id);
    expect(idx('proposed:loyalty')).toBeLessThan(idx('card:proposed:loyalty'));
  });

  it('proposed links dock an unlinked card under the microservice', () => {
    const v = applyOverlay(baseline, draft({ proposedLinks: [{ microservice: 'billing', cardId: 'c2' }] }));
    const c = card(v, 'card:c2');
    expect(c.parentId).toBe('ms:billing');
    expect((c.data as CardView).linkProposed).toBe(true);
    expect(ms(v, 'ms:billing').cardNodeId).toBe('card:c2');
  });

  it('a removed microservice implies its connections and its spec link; changes show before and after', () => {
    const v = applyOverlay(
      baseline,
      draft({ removals: { connections: [], microservices: ['orders'], links: [] }, connectionChanges: [{ connectionId: 'f2', name: 'Paid', kind: 'NONE' }] }),
    );
    expect(ms(v, 'ms:orders').removed).toBe(true);
    expect(ms(v, 'ms:orders').linkRemoved).toBe(true);
    expect((card(v, 'card:c1').data as CardView).removed).toBe(true);
    const f1 = v.edges.find((e) => e.id === 'connection:f1')!;
    expect(f1.data.removed).toBe(true);
    const f2 = v.edges.find((e) => e.id === 'connection:f2')!;
    expect([f2.data.name, f2.data.kind, f2.data.before?.name, f2.data.before?.kind]).toEqual(['Paid', null, 'Invoice paid', 'EVENT']);
    expect(baseline.edges[1]!.data.before).toBeUndefined();
  });

  it('flags proposed or changed endpoints the target spec lacks (FR-055)', () => {
    const v = applyOverlay(
      baseline,
      draft({
        proposedConnections: [
          { id: 'ok', source: 'billing', target: 'orders', name: 'Get', endpoint: 'GET /orders/{id}' },
          { id: 'bad', source: 'billing', target: 'orders', name: 'Nope', endpoint: 'POST /nope' },
          { id: 'nospec', source: 'orders', target: 'billing', name: 'Any', endpoint: 'POST /x' },
        ],
        connectionChanges: [{ connectionId: 'f2', endpoint: 'DELETE /orders/{orderId}' }],
      }),
    );
    const e = (id: string) => v.edges.find((x) => x.id === id)!.data;
    expect(e('proposed:ok').endpointMissing).toBeUndefined();
    expect(e('proposed:bad').endpointMissing).toBe(true);
    expect(e('proposed:nospec').endpointMissing).toBeUndefined();
    expect([e('connection:f2').endpoint, e('connection:f2').endpointMissing, e('connection:f2').before?.endpoint]).toEqual(['DELETE /orders/{orderId}', true, null]);
  });

  it('proposed connections and connection changes carry flows (FR-053)', () => {
    const v = applyOverlay(
      baseline,
      draft({
        proposedConnections: [{ id: 'pf1', source: 'orders', target: 'billing', name: 'Refund', flowId: 'g1' }],
        connectionChanges: [{ connectionId: 'f1', flowId: 'g2' }],
      }),
    );
    expect(v.edges.find((e) => e.id === 'proposed:pf1')!.data.flowId).toBe('g1');
    const f1 = v.edges.find((e) => e.id === 'connection:f1')!;
    expect([f1.data.flowId, f1.data.before?.flowId]).toEqual(['g2', null]);
    const ungrouped = applyOverlay(baseline, draft({ connectionChanges: [{ connectionId: 'f1', flowId: 'NONE' }] }));
    expect(ungrouped.edges.find((e) => e.id === 'connection:f1')!.data.flowId).toBeNull();
  });

  it('nests proposed fields under their object, including baseline array items (002 T108)', () => {
    const at = (name: string) => ({ cardId: 'c1', endpointKey: 'c1:GET /orders/{}', direction: 'OUT' as const, field: { name, type: 'string' } });
    const v = applyOverlay(baseline, draft({ addedFields: [at('items[].discount'), at('shipping'), at('shipping.city')] }));
    const c = card(v, 'card:c1').data as CardView;
    expect(c.endpoints[0]!.outputs.map((f) => f.path)).toEqual(['items', 'items[].productId', 'items[].discount', 'total', 'shipping', 'shipping.city']);
    const city = c.endpoints[0]!.outputs.find((f) => f.path === 'shipping.city')!;
    expect([city.name, city.proposed]).toEqual(['city', true]);
  });

  it('flags orphaned proposals and skips connections whose end no longer exists', () => {
    const v = applyOverlay(
      baseline,
      draft({
        addedEndpoints: [{ cardId: 'c1', endpoint: { method: 'GET', path: '/x' } }],
        proposedConnections: [{ id: 'pf1', source: 'orders', target: 'gone', name: 'Lost' }],
      }),
      [{ ref: 'gone', usedBy: ['proposedConnections[0]', 'addedEndpoints[0]'] }],
    );
    expect(v.edges.find((e) => e.id === 'proposed:pf1')).toBeUndefined();
    const c = card(v, 'card:c1').data as CardView;
    expect(c.endpoints[1]!.orphaned).toBe(true);
  });

  it('scenario positions override baseline ones; untouched nodes keep their identity', () => {
    const v = applyOverlay(baseline, draft({ positions: { 'ms:orders': { x: 10, y: 20 } } }));
    expect(v.nodes.find((n) => n.id === 'ms:orders')!.position).toEqual({ x: 10, y: 20 });
    expect(v.nodes.find((n) => n.id === 'ms:billing')).toBe(baseline.nodes.find((n) => n.id === 'ms:billing'));
    expect(v.nodes.filter((n) => isMicroservice(n.data) || isCard(n.data))).toHaveLength(4);
  });
});
