import { useEffect, useState } from 'react';
import { useReactFlow } from '@xyflow/react';
import { ApiError } from '../api/client';
import { fetchConnectionsDocument, fetchScenario, useCanvas, useCreateScenario, useUpdateScenario } from '../api/queries';
import { useCanvasStore } from '../canvas/canvasStore';
import { ConfirmDialog, Modal } from '../ui/Modal';
import { toast } from '../ui/toastStore';
import { messageOf } from '../ui/errors';
import { canvasFrame } from './canvasFrame';
import { exportCanvasImage } from './export/exportImage';
import { exportScenarioJson } from './export/exportJson';
import { downloadText } from '../connections/document/importExport';
import { baselineView } from '../canvas/viewModel';
import { isMicroservice } from '../canvas/viewModel';
import { applyScenarioToDocument, documentText } from './applyScenarioToDocument';
import { useScenarioStore } from './scenarioStore';

function SaveAsDialog({ initial, onSave, onClose }: { initial: string; onSave: (name: string) => void; onClose: () => void }) {
  const [name, setName] = useState(initial);
  return (
    <Modal
      title="Save scenario as"
      onClose={onClose}
      footer={
        <>
          <button type="button" onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="primary" disabled={name.trim() === ''} onClick={() => onSave(name.trim())}>
            Save
          </button>
        </>
      }
    >
      <label className="stack">
        Name
        <input aria-label="Scenario name" value={name} maxLength={120} autoFocus onChange={(e) => setName(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && name.trim() && onSave(name.trim())} />
      </label>
    </Modal>
  );
}

/** Export → connections document | scenario JSON | image (FR-025, FR-041, FR-043). */
export function ExportMenu() {
  const isDraft = useScenarioStore((s) => s.isDraftMode);
  const name = useScenarioStore((s) => s.name);
  const { data: canvas } = useCanvas();
  const rf = useReactFlow();
  const [open, setOpen] = useState(false);

  const image = async () => {
    setOpen(false);
    if (!canvasFrame.current) return;
    try {
      await exportCanvasImage(rf, canvasFrame.current, isDraft ? name : 'architecture');
    } catch (e) {
      toast.error(`Image export failed: ${messageOf(e)}`);
    }
  };
  const json = async () => {
    setOpen(false);
    try {
      await exportScenarioJson();
    } catch (e) {
      toast.error(`JSON export failed: ${messageOf(e)}`);
    }
  };
  const connections = async (withScenario: boolean) => {
    setOpen(false);
    try {
      const doc = await fetchConnectionsDocument();
      if (doc.excludedDanglingConnections > 0) {
        toast.info(`${doc.excludedDanglingConnections} dangling connection(s) were left out; resolve them in the Dangling panel.`);
      }
      if (!withScenario || !canvas) {
        downloadText(doc.text, 'connections.json');
        return;
      }
      const base = baselineView(canvas);
      const names = new Map<string, string>();
      for (const n of base.nodes) if (isMicroservice(n.data) && n.data.microserviceId) names.set(n.data.microserviceId, n.data.name);
      const keys = new Map(
        canvas.edges.map((e) => {
          const src = names.get(e.source.replace(/^ms:/, '')) ?? '';
          const tgt = names.get(e.target.replace(/^ms:/, '')) ?? '';
          return [e.data.connectionId, { source: src, target: tgt, name: e.data.name }] as const;
        }),
      );
      const flowNames = new Map(canvas.flows.map((g) => [g.id, g.name]));
      const applied = applyScenarioToDocument(doc.document, useScenarioStore.getState().draft, names, keys, flowNames);
      downloadText(documentText(applied), `${name.trim() || 'scenario'}.connections.json`);
    } catch (e) {
      toast.error(`Connections export failed: ${messageOf(e)}`);
    }
  };

  return (
    <div className="menu-anchor">
      <button type="button" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        Export ▾
      </button>
      {open && (
        <div className="dropdown" role="menu">
          <button type="button" role="menuitem" className="menu-item" onClick={() => void connections(false)}>
            Connections document{isDraft ? ' (baseline only)' : ''}
          </button>
          {isDraft && (
            <button type="button" role="menuitem" className="menu-item" onClick={() => void connections(true)}>
              Connections document (with scenario applied)
            </button>
          )}
          <button type="button" role="menuitem" className="menu-item" disabled={!isDraft} onClick={() => void json()} title={isDraft ? '' : 'Enter Draft Scenario Mode to export a scenario'}>
            Scenario JSON
          </button>
          <button type="button" role="menuitem" className="menu-item" onClick={() => void image()}>
            Image (PNG)
          </button>
        </div>
      )}
    </div>
  );
}

/** The green banner and warnings shown inside the canvas frame while drafting. */
export function DraftBanner() {
  const name = useScenarioStore((s) => s.name);
  const dirty = useScenarioStore((s) => s.dirty);
  const orphans = useScenarioStore((s) => s.orphans);
  const drift = useScenarioStore((s) => s.baselineDrift);
  return (
    <div className="draft-banner" role="status">
      <strong>Draft scenario: {name}</strong>
      {dirty && <span className="dirty-dot" title="Unsaved changes"> ● unsaved</span>}
      {orphans.length > 0 && (
        <span className="banner-warning" title={orphans.map((o) => o.ref).join('\n')}>
          ⚠ {orphans.length} reference{orphans.length === 1 ? '' : 's'} to baseline items that no longer exist
        </span>
      )}
      {drift.length > 0 && (
        <span className="banner-warning">
          {drift.length} spec{drift.length === 1 ? '' : 's'} changed since the scenario was saved
        </span>
      )}
    </div>
  );
}

/** Draft Scenario Mode toggle, Save / Save as, with the unsaved-changes guards (FR-030, FR-034). */
export function ScenarioToolbar() {
  const s = useScenarioStore();
  const openPanel = useCanvasStore((st) => st.openPanel);
  const clearHighlight = useCanvasStore((st) => st.clearHighlight);
  const create = useCreateScenario();
  const update = useUpdateScenario();
  const [saveAs, setSaveAs] = useState(false);
  const [confirmExit, setConfirmExit] = useState(false);
  const [conflict, setConflict] = useState(false);

  useEffect(() => {
    if (!s.dirty) return;
    const guard = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    window.addEventListener('beforeunload', guard);
    return () => window.removeEventListener('beforeunload', guard);
  }, [s.dirty]);

  const exit = () => {
    s.exitDraft();
    s.reset();
    clearHighlight();
    openPanel('none');
  };

  const doCreate = (name: string) => {
    useScenarioStore.setState({ name });
    const write = useScenarioStore.getState().toScenarioWrite();
    create.mutate(
      { name, description: write.description, content: write.content },
      {
        onSuccess: (saved) => {
          s.markSaved(saved);
          setSaveAs(false);
          setConflict(false);
          toast.success(`Saved "${saved.name}"`);
        },
        onError: (e) => toast.error(messageOf(e)),
      },
    );
  };

  const save = () => {
    if (!s.scenarioId || s.version === null) {
      setSaveAs(true);
      return;
    }
    const write = s.toScenarioWrite();
    update.mutate(
      { id: s.scenarioId, body: { ...write, version: s.version } },
      {
        onSuccess: (saved) => {
          s.markSaved(saved);
          toast.success(`Saved "${saved.name}"`);
        },
        onError: (e) => {
          if (e instanceof ApiError && e.status === 409 && e.title.startsWith('Changed')) setConflict(true);
          else toast.error(messageOf(e));
        },
      },
    );
  };

  const reload = async () => {
    if (!s.scenarioId) return;
    try {
      const fresh = await fetchScenario(s.scenarioId);
      s.load(fresh);
      setConflict(false);
    } catch (e) {
      toast.error(messageOf(e));
    }
  };

  if (!s.isDraftMode) {
    return (
      <div className="button-group">
        <button
          type="button"
          className="draft-toggle"
          aria-pressed={false}
          onClick={() => {
            s.reset();
            s.enterDraft();
            clearHighlight();
          }}
        >
          Draft Scenario Mode
        </button>
        <button type="button" onClick={() => openPanel('scenarios')}>
          Scenarios
        </button>
      </div>
    );
  }

  return (
    <div className="button-group">
      <button type="button" className="draft-toggle active" aria-pressed onClick={() => (s.dirty ? setConfirmExit(true) : exit())}>
        Exit draft
      </button>
      <button type="button" onClick={() => openPanel('proposals')}>
        Proposals
      </button>
      <button type="button" className="primary" disabled={create.isPending || update.isPending} onClick={save}>
        Save
      </button>
      <button type="button" onClick={() => setSaveAs(true)}>
        Save as
      </button>
      <button type="button" onClick={() => openPanel('scenarios')}>
        Scenarios
      </button>
      {saveAs && <SaveAsDialog initial={s.scenarioId ? `${s.name} (copy)` : s.name} onSave={doCreate} onClose={() => setSaveAs(false)} />}
      {confirmExit && (
        <ConfirmDialog
          title="Leave Draft Scenario Mode"
          message={<p>The draft has unsaved changes. Leave and discard them?</p>}
          confirmLabel="Discard and leave"
          danger
          onCancel={() => setConfirmExit(false)}
          onConfirm={() => {
            setConfirmExit(false);
            exit();
          }}
        />
      )}
      {conflict && (
        <Modal
          title="Changed in another tab"
          onClose={() => setConflict(false)}
          footer={
            <>
              <button type="button" onClick={() => void reload()}>
                Reload theirs
              </button>
              <button type="button" className="primary" onClick={() => doCreate(`${s.name} (copy)`)}>
                Save mine as a copy
              </button>
            </>
          }
        >
          <p>This scenario was saved elsewhere since you opened it. Saving now would overwrite those changes.</p>
        </Modal>
      )}
    </div>
  );
}
