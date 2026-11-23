import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { EndpointSelect } from '../../src/connections/EndpointSelect';
import { targetEndpoints } from '../../src/canvas/viewModel';
import { canvas, endpoint, view } from '../support/fixtures';

describe('connection endpoint on a collapsed card (regression)', () => {
  const g = view(
    canvas(
      [
        { id: 'orders', name: 'Orders' },
        { id: 'catalog', name: 'Catalog', card: 'c1', collapsed: true },
      ],
      [{ id: 'c1', endpoints: [endpoint('c1', 'GET', '/products/{productId}')] }],
    ),
  );

  it('keeps the endpoints of a card collapsed to its API badge', () => {
    expect(g.nodes.find((n) => n.id === 'card:c1')!.hidden).toBe(true);
    expect(targetEndpoints(g, 'ms:catalog')).toEqual(['GET /products/{productId}']);
  });

  it('shows no ⚠ when the endpoint exists, even with different parameter names', () => {
    render(<EndpointSelect options={targetEndpoints(g, 'ms:catalog')} value="GET /products/{id}" onChange={() => undefined} />);
    expect(screen.queryByText(/not in the spec/)).not.toBeInTheDocument();
    expect((screen.getByLabelText('Connection endpoint') as HTMLSelectElement).value).toBe('GET /products/{productId}');
  });

  it('still shows ⚠ for an endpoint the spec really lacks', () => {
    render(<EndpointSelect options={targetEndpoints(g, 'ms:catalog')} value="POST /products" onChange={() => undefined} />);
    expect(screen.getByText(/POST \/products \(not in the spec\)/)).toBeInTheDocument();
  });
});
