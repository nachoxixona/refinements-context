import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactFlow, ReactFlowProvider, type NodeTypes } from '@xyflow/react';
import { SpecCardNode } from '../../src/canvas/nodes/SpecCardNode';
import { MethodBadge } from '../../src/canvas/nodes/MethodBadge';
import type { CardConnectionNode, CardUiData } from '../../src/canvas/nodes/nodeTypes';
import { useCanvasStore } from '../../src/canvas/canvasStore';
import type { Endpoint } from '../../src/api/types';
import { endpoint, field } from '../support/fixtures';

// jsdom never measures nodes, so React Connection keeps them visibility:hidden; query by label/text, not role.
const nodeTypes: NodeTypes = { specCard: SpecCardNode };

function data(endpoints: Endpoint[], overrides: Partial<CardUiData> = {}): CardUiData {
  return {
    kind: 'card',
    cardId: 'c1',
    keyPrefix: 'c1',
    title: 'Order Service',
    apiVersion: '1.4.0',
    endpoints,
    ownerId: null,
    ownerName: null,
    highlight: 'none',
    draftMode: false,
    baseline: {
      cardId: 'c1',
      title: 'Order Service',
      sourceType: 'URL',
      sourceUrl: 'http://specs.local/orders.yaml',
      specFormat: 'OPENAPI_3_0',
      contentHash: 'h',
      lastSyncAt: '2026-11-22T10:00:00Z',
      lastSyncStatus: 'OK',
      linkedMicroserviceId: null,
    },
    ...overrides,
  };
}

function renderNode(d: CardUiData, zoom: number) {
  const nodes: CardConnectionNode[] = [{ id: 'card:c1', type: 'specCard', position: { x: 0, y: 0 }, data: d }];
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <ReactFlowProvider>
        <div style={{ width: 1000, height: 800 }}>
          <ReactFlow nodes={nodes} edges={[]} nodeTypes={nodeTypes} defaultViewport={{ x: 0, y: 0, zoom }} minZoom={0.1} />
        </div>
      </ReactFlowProvider>
    </QueryClientProvider>,
  );
}

const eps = [
  endpoint('c1', 'GET', '/orders/{orderId}', [field('orderId', 'PATH')], [field('customerId', 'RESPONSE'), field('items', 'RESPONSE', 'array'), field('items[].productId', 'RESPONSE')]),
  endpoint('c1', 'POST', '/orders', [field('customerId', 'BODY')], []),
  endpoint('c1', 'DELETE', '/orders/{orderId}'),
];

beforeEach(() => {
  useCanvasStore.setState({ expandedEndpoints: {}, collapsedNodes: {}, showAllEndpoints: {} });
});

describe('SpecCardNode', () => {
  it('shows only the title and endpoint count below 0.45 zoom', () => {
    renderNode(data(eps), 0.3);
    expect(screen.getByText('Order Service')).toBeInTheDocument();
    expect(screen.getByText('3 endpoints')).toBeInTheDocument();
    expect(screen.queryByText('/orders')).not.toBeInTheDocument();
  });

  it('shows endpoint rows with badges between 0.45 and 0.9, without field expansion', () => {
    renderNode(data(eps), 0.6);
    expect(screen.getByText('/orders')).toBeInTheDocument();
    expect(screen.getAllByText('/orders/{orderId}')).toHaveLength(2);
    expect(screen.queryByLabelText(/^Expand/)).not.toBeInTheDocument();
  });

  it('shows where the spec came from and when, and that it is unlinked (FR-009)', () => {
    renderNode(data(eps), 0.6);
    expect(screen.getByText(/http:\/\/specs\.local\/orders\.yaml/)).toBeInTheDocument();
    expect(screen.getByText(/unlinked/)).toBeInTheDocument();
  });

  it('expands an endpoint at 0.9 and above to list inputs and outputs with nesting', async () => {
    renderNode(data(eps), 1);
    await userEvent.click(screen.getByLabelText('Expand GET /orders/{orderId}'));
    expect(screen.getByText('IN · request')).toBeInTheDocument();
    expect(screen.getByText('OUT · response')).toBeInTheDocument();
    const nested = screen.getByTitle('items[].productId');
    const top = screen.getByTitle('customerId');
    expect(parseInt(nested.style.paddingLeft)).toBeGreaterThan(parseInt(top.style.paddingLeft));
  });

  it('offers nested add-field buttons on object and array fields in draft mode', async () => {
    renderNode(data(eps, { draftMode: true }), 1);
    await userEvent.click(screen.getByLabelText('Expand GET /orders/{orderId}'));
    expect(screen.getByLabelText('Add field inside items')).toBeInTheDocument();
    expect(screen.getAllByText('+ Add field').length).toBeGreaterThan(0);
  });

  it('method badges carry a color class and a text label', () => {
    render(<MethodBadge method="DELETE" />);
    const badge = screen.getByText('DELETE');
    expect(badge).toHaveClass('method-badge', 'method-delete');
    expect(badge).toHaveAccessibleName('HTTP DELETE');
  });

  it('caps long endpoint lists with Show all', async () => {
    const many = Array.from({ length: 30 }, (_, i) => endpoint('c1', 'GET', `/r${i}`));
    renderNode(data(many), 0.6);
    expect(screen.getByText('/r24')).toBeInTheDocument();
    expect(screen.queryByText('/r25')).not.toBeInTheDocument();
    await userEvent.click(screen.getByText('Show all (5 more)'));
    expect(screen.getByText('/r29')).toBeInTheDocument();
  });

  it('shows the empty indicator and the sync failed badge', () => {
    const d = data([]);
    renderNode({ ...d, baseline: { ...d.baseline!, lastSyncStatus: 'FAILED' } }, 0.6);
    expect(screen.getByText('No endpoints in this spec')).toBeInTheDocument();
    expect(screen.getByText('sync failed')).toBeInTheDocument();
  });
});
