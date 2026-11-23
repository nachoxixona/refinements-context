import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

// CodeMirror needs layout APIs jsdom lacks; a textarea stands in for it here.
vi.mock('@uiw/react-codemirror', () => ({
  default: ({ value, onChange }: { value: string; onChange: (v: string) => void }) => (
    <textarea aria-label="Connections document text" value={value} onChange={(e) => onChange(e.target.value)} />
  ),
  EditorView: { lineWrapping: {} },
}));

import { diagnostics, ConnectionsDocumentEditor, offsetOf } from '../../src/connections/document/ConnectionsDocumentEditor';

const DOC = '{\n  "formatVersion": 1,\n  "connections": []\n}\n';
const changes = (over: Record<string, unknown> = {}) => ({
  microservicesAdded: [],
  microservicesChanged: [],
  connectionsAdded: [{ connectionId: null, name: 'Pay', source: 'A', target: 'B', kindAfter: null }],
  connectionsChanged: [],
  connectionsRemoved: [],
  unchanged: false,
  ...over,
});

let calls: { method: string; url: string; body: unknown }[];
let responses: Record<string, () => Response>;

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

beforeEach(() => {
  calls = [];
  responses = {
    'GET /api/connections-document': () => json(200, { document: {}, text: DOC, revision: 7, excludedDanglingConnections: 0 }),
    'POST /api/connections-document/preview': () => json(200, { changes: changes(), revision: 7 }),
    'PUT /api/connections-document': () => json(200, { changes: changes(), revision: 8 }),
  };
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const method = init?.method ?? 'GET';
    const url = String(input);
    calls.push({ method, url, body: init?.body ? JSON.parse(String(init.body)) : undefined });
    const r = responses[`${method} ${url}`];
    return r ? r() : json(404, { title: 'not found' });
  });
});

afterEach(() => vi.restoreAllMocks());

function renderEditor(onClose = vi.fn()) {
  render(
    <QueryClientProvider client={new QueryClient()}>
      <ConnectionsDocumentEditor onClose={onClose} />
    </QueryClientProvider>,
  );
  return onClose;
}

describe('ConnectionsDocumentEditor (research R12)', () => {
  it('save runs preview, shows the changes, then applies with the preview revision', async () => {
    renderEditor();
    const editor = await screen.findByLabelText("Connections document text");
    await waitFor(() => expect((editor as HTMLTextAreaElement).value).toBe(DOC));
    await userEvent.click(screen.getByText('Save…'));
    expect(await screen.findByText('Connections added (1)')).toBeInTheDocument();
    expect(calls.filter((c) => c.method === 'PUT')).toHaveLength(0);
    await userEvent.click(screen.getByText('Apply'));
    await waitFor(() => expect(calls.some((c) => c.method === 'PUT')).toBe(true));
    const put = calls.find((c) => c.method === 'PUT')!;
    expect(put.body).toEqual({ text: DOC, baseRevision: 7 });
  });

  it('a 422 lists the errors with their lines and applies nothing', async () => {
    responses['POST /api/connections-document/preview'] = () =>
      json(422, {
        title: 'Invalid connections document',
        errors: [{ kind: 'MISSING', pointer: '/connections/0', path: 'connections[0].target', line: 3, column: 5, message: 'connections[0].target is missing' }],
      });
    renderEditor();
    await screen.findByLabelText("Connections document text");
    await waitFor(() => expect(calls.length).toBe(1));
    await userEvent.click(screen.getByText('Save…'));
    expect(await screen.findByText('connections[0].target is missing (line 3:5)')).toBeInTheDocument();
    expect(calls.some((c) => c.method === 'PUT')).toBe(false);
  });

  it('closing with unsaved changes asks to discard', async () => {
    const onClose = renderEditor();
    const editor = await screen.findByLabelText("Connections document text");
    await waitFor(() => expect((editor as HTMLTextAreaElement).value).toBe(DOC));
    await userEvent.type(editor, ' ');
    await userEvent.click(screen.getByLabelText('Close'));
    expect(screen.getByText('The connections document has unsaved changes. Discard them?')).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
    await userEvent.click(screen.getByText('Discard'));
    expect(onClose).toHaveBeenCalled();
  });

  it('maps backend positions to editor offsets', () => {
    const text = 'ab\ncdef\ng';
    expect(offsetOf(text, 2, 3)).toBe(5);
    const [d] = diagnostics(text, [{ kind: 'MISSING', line: 2, column: 1, message: 'x' }]);
    expect([d!.from, d!.to, d!.message]).toEqual([3, 7, 'x']);
  });
});
