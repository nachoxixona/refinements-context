import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactFlow, ReactFlowProvider, type NodeTypes } from '@xyflow/react';
import { MicroserviceNode } from '../../src/canvas/nodes/MicroserviceNode';
import type { MicroserviceConnectionNode, MicroserviceUiData } from '../../src/canvas/nodes/nodeTypes';
import { useCanvasStore } from '../../src/canvas/canvasStore';

const nodeTypes: NodeTypes = { microservice: MicroserviceNode };

function data(overrides: Partial<MicroserviceUiData> = {}): MicroserviceUiData {
  return {
    kind: 'microservice',
    microserviceId: 'm1',
    ref: 'm1',
    name: 'Orders',
    description: 'Order lifecycle',
    cardNodeId: null,
    cardTitle: null,
    cardCollapsed: false,
    specCardId: null,
    highlight: 'none',
    draftMode: false,
    connectionCount: 0,
    ...overrides,
  };
}

function renderNode(d: MicroserviceUiData, zoom = 1) {
  const nodes: MicroserviceConnectionNode[] = [{ id: 'ms:m1', type: 'microservice', position: { x: 0, y: 0 }, data: d }];
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <ReactFlowProvider>
        <div style={{ width: 1000, height: 800 }}>
          <ReactFlow nodes={nodes} edges={[]} nodeTypes={nodeTypes} defaultViewport={{ x: 0, y: 0, zoom }} />
        </div>
      </ReactFlowProvider>
    </QueryClientProvider>,
  );
}

describe('MicroserviceNode', () => {
  it('shows the name, the description and that it has no spec', () => {
    renderNode(data());
    expect(screen.getByText('Orders')).toBeInTheDocument();
    expect(screen.getByText('Order lifecycle')).toBeInTheDocument();
    expect(screen.getByText('no spec')).toBeInTheDocument();
  });

  it('shows the API badge when the docked card is collapsed and expands it on click (FR-014)', async () => {
    const toggle = vi.fn();
    useCanvasStore.setState({ actions: { toggleCardCollapsed: toggle, applyLayout: vi.fn() } });
    renderNode(data({ specCardId: 'c1', cardTitle: 'Orders API', cardCollapsed: true }));
    await userEvent.click(screen.getByText('API: Orders API'));
    expect(toggle).toHaveBeenCalledWith('c1');
  });

  it('offers a collapse toggle while a card is docked, but not in draft mode', async () => {
    const toggle = vi.fn();
    useCanvasStore.setState({ actions: { toggleCardCollapsed: toggle, applyLayout: vi.fn() } });
    const { unmount } = renderNode(data({ specCardId: 'c1', cardTitle: 'Orders API', cardNodeId: 'card:c1' }));
    await userEvent.click(screen.getByLabelText('Collapse spec Orders API'));
    expect(toggle).toHaveBeenCalledWith('c1');
    unmount();
    renderNode(data({ specCardId: 'c1', cardTitle: 'Orders API', cardNodeId: 'card:c1', draftMode: true }));
    expect(screen.queryByLabelText('Collapse spec Orders API')).not.toBeInTheDocument();
  });

  it('marks proposed and proposed-removal microservices', () => {
    const { unmount } = renderNode(data({ proposed: true, microserviceId: null, ref: 'proposed:loyalty' }));
    expect(screen.getByText('PROPOSED')).toBeInTheDocument();
    unmount();
    renderNode(data({ removed: true, draftMode: true }));
    expect(screen.getByText('PROPOSED REMOVAL')).toBeInTheDocument();
  });

  it('opens its menu with the delete warning count', async () => {
    renderNode(data({ connectionCount: 3 }));
    await userEvent.click(screen.getByLabelText('Actions for Orders'));
    await userEvent.click(screen.getByText('Delete microservice'));
    expect(screen.getByText(/3 connections reference it/)).toBeInTheDocument();
  });
});
