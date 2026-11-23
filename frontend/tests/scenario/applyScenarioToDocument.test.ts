import { describe, expect, it } from 'vitest';
import type { ConnectionsDocument } from '../../src/api/types';
import { applyScenarioToDocument, documentText } from '../../src/scenario/applyScenarioToDocument';
import { emptyDraft } from '../../src/scenario/scenarioStore';

const doc: ConnectionsDocument = {
  formatVersion: 2,
  microservices: [{ name: 'Billing' }, { name: 'Legacy' }, { name: 'Orders' }],
  connections: [
    { name: 'Create invoice', source: 'Orders', target: 'Billing', kind: 'request' },
    { name: 'Invoice paid', source: 'Billing', target: 'Orders', kind: 'event' },
    { name: 'Old call', source: 'Orders', target: 'Legacy' },
  ],
};
const names = new Map([
  ['m-orders', 'Orders'],
  ['m-billing', 'Billing'],
  ['m-legacy', 'Legacy'],
]);
const connectionKeys = new Map([
  ['f1', { source: 'Orders', target: 'Billing', name: 'Create invoice' }],
  ['f2', { source: 'Billing', target: 'Orders', name: 'Invoice paid' }],
  ['f3', { source: 'Orders', target: 'Legacy', name: 'Old call' }],
]);

describe('applyScenarioToDocument (FR-043)', () => {
  it('adds proposals, drops removals and applies changes', () => {
    const out = applyScenarioToDocument(
      doc,
      {
        ...emptyDraft(),
        proposedMicroservices: [{ tempKey: 'proposed:loyalty', name: 'Loyalty', description: 'Points' }],
        proposedConnections: [{ id: 'p1', source: 'm-orders', target: 'proposed:loyalty', name: 'Points earned', kind: 'EVENT' }],
        removals: { connections: ['f1'], microservices: ['m-legacy'], links: [] },
        connectionChanges: [{ connectionId: 'f2', kind: 'REQUEST', name: 'Paid' }],
      },
      names,
      connectionKeys,
    );
    expect(out.microservices?.map((m) => m.name)).toEqual(['Billing', 'Orders', 'Loyalty']);
    expect(out.connections).toEqual([
      { name: 'Paid', source: 'Billing', target: 'Orders', kind: 'request' },
      { name: 'Points earned', source: 'Orders', target: 'Loyalty', kind: 'event' },
    ]);
  });

  it('keeps flows and applies proposed flow assignments by name', () => {
    const withFlows: ConnectionsDocument = { ...doc, flows: [{ name: 'Checkout', color: 'blue' }], connections: [{ ...doc.connections[0]!, flow: 'Checkout' }, doc.connections[1]!] };
    const out = applyScenarioToDocument(
      withFlows,
      {
        ...emptyDraft(),
        proposedConnections: [{ id: 'p1', source: 'm-billing', target: 'm-orders', name: 'Refund', flowId: 'g1' }],
        connectionChanges: [{ connectionId: 'f1', flowId: 'NONE' }, { connectionId: 'f2', flowId: 'g1' }],
      },
      names,
      connectionKeys,
      new Map([['g1', 'Checkout']]),
    );
    expect(out.flows).toEqual([{ name: 'Checkout', color: 'blue' }]);
    expect(out.connections.map((f) => [f.name, f.flow])).toEqual([
      ['Create invoice', undefined],
      ['Invoice paid', 'Checkout'],
      ['Refund', 'Checkout'],
    ]);
    expect(Object.keys(out)).toEqual(['formatVersion', 'microservices', 'flows', 'connections']);
  });

  it('applies endpoints of proposed connections and endpoint changes (FR-056)', () => {
    const out = applyScenarioToDocument(
      { ...doc, connections: [{ ...doc.connections[0]!, endpoint: 'POST /invoices' }, doc.connections[1]!] },
      {
        ...emptyDraft(),
        proposedConnections: [{ id: 'p1', source: 'm-billing', target: 'm-orders', name: 'Refund', endpoint: 'POST /refunds' }],
        connectionChanges: [{ connectionId: 'f1', endpoint: 'NONE' }, { connectionId: 'f2', endpoint: 'GET /orders/{id}' }],
      },
      names,
      connectionKeys,
    );
    expect(out.connections.map((f) => [f.name, f.endpoint])).toEqual([
      ['Create invoice', undefined],
      ['Invoice paid', 'GET /orders/{id}'],
      ['Refund', 'POST /refunds'],
    ]);
  });

  it('kind NONE removes the kind; an empty draft returns the same document', () => {
    const out = applyScenarioToDocument(doc, { ...emptyDraft(), connectionChanges: [{ connectionId: 'f1', kind: 'NONE' }] }, names, connectionKeys);
    expect(out.connections[0]).toEqual({ name: 'Create invoice', source: 'Orders', target: 'Billing' });
    expect(applyScenarioToDocument(doc, emptyDraft(), names, connectionKeys)).toEqual(doc);
    expect(documentText(doc).endsWith('}\n')).toBe(true);
  });
});
