import { useRef, useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactFlowProvider } from '@xyflow/react';
import { useCanvas } from './api/queries';
import { ArchitectureCanvas } from './canvas/ArchitectureCanvas';
import { useCanvasStore } from './canvas/canvasStore';
import { PresentationToggle } from './canvas/PresentationToggle';
import { SearchBar } from './canvas/search/SearchBar';
import { ImportSpecDialog } from './cards/ImportSpecDialog';
import { DanglingConnectionsPanel } from './connections/DanglingConnectionsPanel';
import { ConnectionDetailsPanel } from './connections/ConnectionDetailsPanel';
import { FlowsPanel } from './connections/FlowsPanel';
import { ConnectionsDocumentEditor } from './connections/document/ConnectionsDocumentEditor';
import { readFile } from './connections/document/importExport';
import { useApplyDocument } from './connections/document/useApplyDocument';
import { MicroserviceForm } from './microservices/MicroserviceForm';
import { canvasFrame } from './scenario/canvasFrame';
import { ProposedMicroserviceForm } from './scenario/ProposedMicroserviceForm';
import { ScenarioList } from './scenario/ScenarioList';
import { DraftBanner, ExportMenu, ScenarioToolbar } from './scenario/ScenarioToolbar';
import { useScenarioStore } from './scenario/scenarioStore';
import { Toasts } from './ui/Toasts';
import { UndoButtons } from './history/UndoButtons';

const queryClient = new QueryClient({
  defaultOptions: { queries: { refetchOnWindowFocus: false, retry: 1 } },
});

function SidePanel() {
  const panel = useCanvasStore((s) => s.panel);
  const openPanel = useCanvasStore((s) => s.openPanel);
  const selectEdge = useCanvasStore((s) => s.selectEdge);
  if (panel === 'none') return null;
  const close = () => (panel === 'connection' ? selectEdge(null) : openPanel('none'));
  return (
    <aside className="side-panel" aria-label="Side panel">
      <button type="button" className="icon-button panel-close" aria-label="Close panel" onClick={close}>
        ✕
      </button>
      {panel === 'connection' && <ConnectionDetailsPanel />}
      {panel === 'dangling' && <DanglingConnectionsPanel />}
      {panel === 'flows' && <FlowsPanel />}
      {panel === 'scenarios' && <ScenarioList />}
      {panel === 'proposals' && <ProposedMicroserviceForm />}
    </aside>
  );
}

/** Import connections: file → preview → apply (FR-020). */
function ImportConnectionsButton({ disabled }: { disabled: boolean }) {
  const file = useRef<HTMLInputElement>(null);
  const { start, dialog } = useApplyDocument({});
  return (
    <>
      <button type="button" disabled={disabled} onClick={() => file.current?.click()} title={disabled ? 'Leave draft mode to change the baseline connections' : ''}>
        Import connections
      </button>
      <input
        ref={file}
        type="file"
        accept="application/json,.json"
        hidden
        aria-label="Import connections document"
        onChange={async (e) => {
          const f = e.target.files?.[0];
          e.target.value = '';
          if (f) await start(await readFile(f));
        }}
      />
      {dialog}
    </>
  );
}

function Shell() {
  const presentation = useCanvasStore((s) => s.presentationMode);
  const openPanel = useCanvasStore((s) => s.openPanel);
  const isDraft = useScenarioStore((s) => s.isDraftMode);
  const { data: canvas } = useCanvas();
  const [importOpen, setImportOpen] = useState(false);
  const [msOpen, setMsOpen] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const dangling = canvas?.danglingConnections.length ?? 0;
  const baselineOnly = isDraft ? 'Leave draft mode to change the baseline' : '';

  return (
    <div className={`app${presentation ? ' presentation' : ''}`}>
      <header className="toolbar">
        <span className="brand">Architecture Visualizer</span>
        <UndoButtons />
        <button type="button" className="primary" disabled={isDraft} title={baselineOnly} onClick={() => setImportOpen(true)}>
          Import spec
        </button>
        <button type="button" disabled={isDraft} title={baselineOnly} onClick={() => setMsOpen(true)}>
          New microservice
        </button>
        <ImportConnectionsButton disabled={isDraft} />
        <button type="button" disabled={isDraft} title={baselineOnly} onClick={() => setEditorOpen(true)}>
          Edit connections
        </button>
        <SearchBar />
        <button type="button" className={dangling > 0 ? 'warning' : ''} onClick={() => openPanel('dangling')}>
          Dangling{dangling > 0 ? ` (${dangling})` : ''}
        </button>
        <span className="toolbar-spacer" />
        <ScenarioToolbar />
        <ExportMenu />
        <PresentationToggle />
      </header>
      <main className="workspace">
        <div
          className={`canvas-frame${isDraft ? ' draft' : ''}`}
          ref={(el) => {
            canvasFrame.current = el;
          }}
        >
          {isDraft && <DraftBanner />}
          <div className="canvas-host">
            <ArchitectureCanvas />
          </div>
        </div>
        <SidePanel />
      </main>
      {importOpen && <ImportSpecDialog onClose={() => setImportOpen(false)} />}
      {msOpen && <MicroserviceForm onClose={() => setMsOpen(false)} />}
      {editorOpen && <ConnectionsDocumentEditor onClose={() => setEditorOpen(false)} />}
      <Toasts />
    </div>
  );
}

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ReactFlowProvider>
        <Shell />
      </ReactFlowProvider>
    </QueryClientProvider>
  );
}
