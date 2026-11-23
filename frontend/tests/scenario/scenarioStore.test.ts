import { beforeEach, describe, expect, it } from 'vitest';
import { useScenarioStore } from '../../src/scenario/scenarioStore';

const s = () => useScenarioStore.getState();

beforeEach(() => s().reset());

describe('scenarioStore (content v2)', () => {
  it('adds and removes proposed microservices, cascading their connections and links', () => {
    const key = s().addProposedMicroservice('Loyalty', null, { title: 'Loyalty API', endpoints: [] });
    expect(key).toBe('proposed:loyalty');
    expect(s().addProposedMicroservice('Loyalty')).toBe('proposed:loyalty-2');
    s().addProposedConnection({ source: 'm1', target: key, name: 'Earn' });
    s().addProposedLink(key, 'c9');
    s().setPosition(key, { x: 1, y: 2 });
    s().removeProposedMicroservice(key);
    expect(s().draft.proposedMicroservices.map((p) => p.tempKey)).toEqual(['proposed:loyalty-2']);
    expect(s().draft.proposedConnections).toEqual([]);
    expect(s().draft.proposedLinks).toEqual([]);
    expect(s().draft.positions[key]).toBeUndefined();
    expect(s().dirty).toBe(true);
  });

  it('edits a proposed microservice after creating it (regression)', () => {
    const key = s().addProposedMicroservice('Loyalty', 'first');
    s().updateProposedMicroservice(key, { description: 'Points and tiers' });
    s().updateProposedMicroservice(key, { name: 'Loyalty 2' });
    expect(s().draft.proposedMicroservices[0]).toMatchObject({ tempKey: key, name: 'Loyalty 2', description: 'Points and tiers' });
    s().updateProposedMicroservice(key, { name: '  ', description: null });
    expect(s().draft.proposedMicroservices[0]).toMatchObject({ name: 'Loyalty 2', description: null });
  });

  it('refuses self connections, blank names and duplicate proposed connections', () => {
    expect(s().addProposedConnection({ source: 'a', target: 'a', name: 'x' })).toBeNull();
    expect(s().addProposedConnection({ source: 'a', target: 'b', name: ' ' })).toBeNull();
    expect(s().addProposedConnection({ source: 'a', target: 'b', name: 'Pay' })).not.toBeNull();
    expect(s().addProposedConnection({ source: 'a', target: 'b', name: ' pay ' })).toBeNull();
    expect(s().addProposedConnection({ source: 'b', target: 'a', name: 'Pay' })).not.toBeNull();
  });

  it('toggles removals and sets and clears connection changes', () => {
    s().toggleRemoval('connections', 'f1');
    s().toggleRemoval('microservices', 'm1');
    expect(s().draft.removals).toEqual({ connections: ['f1'], microservices: ['m1'], links: [] });
    s().toggleRemoval('connections', 'f1');
    expect(s().draft.removals.connections).toEqual([]);
    s().setConnectionChange('f2', { kind: 'REQUEST' });
    s().setConnectionChange('f2', { name: 'Paid' });
    expect(s().draft.connectionChanges).toEqual([{ connectionId: 'f2', kind: 'REQUEST', name: 'Paid' }]);
    s().clearConnectionChange('f2');
    expect(s().draft.connectionChanges).toEqual([]);
  });

  it('a card links to one microservice at a time', () => {
    s().addProposedLink('m1', 'c1');
    s().addProposedLink('m2', 'c1');
    expect(s().draft.proposedLinks).toEqual([{ microservice: 'm2', cardId: 'c1' }]);
    s().removeProposedLink('c1');
    expect(s().draft.proposedLinks).toEqual([]);
  });

  it('proposes nested fields on baseline endpoints and removes a parent with its children', () => {
    s().addField('c1', 'c1:GET /orders/{}', 'OUT', { name: 'shipping', type: 'object' });
    s().addField('c1', 'c1:GET /orders/{}', 'OUT', { name: 'shipping.city', type: 'string' });
    s().addField('c1', 'c1:GET /orders/{}', 'OUT', { name: 'items[].discount', type: 'number' });
    expect(s().draft.addedFields.map((f) => f.field.name)).toEqual(['shipping', 'shipping.city', 'items[].discount']);
    s().removeField('c1', 'c1:GET /orders/{}', 'OUT', 'shipping');
    expect(s().draft.addedFields.map((f) => f.field.name)).toEqual(['items[].discount']);
  });

  it('fields on added endpoints and proposed cards live on those endpoints', () => {
    s().addEndpoint('c1', { method: 'GET', path: '/points' });
    s().addField('c1', 'c1:GET /points', 'OUT', { name: 'balance' });
    expect(s().draft.addedEndpoints[0]!.endpoint.outputs).toEqual([{ name: 'balance' }]);
    const key = s().addProposedMicroservice('Loyalty');
    s().addEndpoint(key, { method: 'POST', path: '/earn' });
    expect(s().draft.proposedMicroservices[0]!.card?.title).toBe('Loyalty');
    s().addField(key, `${key}:POST /earn`, 'IN', { name: 'orderId' });
    expect(s().draft.proposedMicroservices[0]!.card?.endpoints[0]!.inputs).toEqual([{ name: 'orderId' }]);
    expect(s().draft.addedFields).toEqual([]);
  });

  it('exports formatVersion 3 documents', () => {
    s().setName('Plan');
    expect(s().toScenarioDocument().formatVersion).toBe(3);
    expect(s().toScenarioWrite().name).toBe('Plan');
  });
});
