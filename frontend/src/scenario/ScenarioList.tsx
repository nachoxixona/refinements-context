import { useRef, useState } from 'react';
import { fetchScenario, useDeleteScenario, useImportScenario, useScenarios, useUpdateScenario } from '../api/queries';
import { ConfirmDialog } from '../ui/Modal';
import { toast } from '../ui/toastStore';
import { messageOf } from '../ui/errors';
import { useScenarioStore } from './scenarioStore';

/** Saved scenarios: open, rename, delete, import (FR-040 – FR-042) */
export function ScenarioList() {
  const { data, isLoading } = useScenarios();
  const del = useDeleteScenario();
  const update = useUpdateScenario();
  const importScenario = useImportScenario();
  const load = useScenarioStore((s) => s.load);
  const reset = useScenarioStore((s) => s.reset);
  const exitDraft = useScenarioStore((s) => s.exitDraft);
  const openId = useScenarioStore((s) => s.scenarioId);
  const dirty = useScenarioStore((s) => s.dirty);
  const file = useRef<HTMLInputElement>(null);
  const [renaming, setRenaming] = useState<{ id: string; name: string } | null>(null);
  const [deleting, setDeleting] = useState<{ id: string; name: string } | null>(null);
  const [pendingOpen, setPendingOpen] = useState<string | null>(null);

  const open = async (id: string) => {
    try {
      const s = await fetchScenario(id);
      load(s);
      if (s.orphans.length > 0) toast.error(`"${s.name}" references ${s.orphans.length} baseline item(s) that no longer exist; they are flagged.`);
    } catch (e) {
      toast.error(messageOf(e));
    }
  };

  const rename = async (id: string, name: string) => {
    try {
      const current = await fetchScenario(id);
      const saved = await update.mutateAsync({
        id,
        body: { name, description: current.description, content: current.content, version: current.version },
      });
      if (openId === id) useScenarioStore.setState({ name: saved.name, version: saved.version });
      setRenaming(null);
    } catch (e) {
      toast.error(messageOf(e));
    }
  };

  const onFile = async (f: File | undefined) => {
    if (!f) return;
    try {
      const doc: unknown = JSON.parse(await f.text());
      const imported = await importScenario.mutateAsync(doc);
      load(imported);
      toast.success(`Imported "${imported.name}"`);
    } catch (e) {
      toast.error(`Import failed: ${messageOf(e)}`);
    } finally {
      if (file.current) file.current.value = '';
    }
  };

  return (
    <div className="panel-body">
      <h3 className="panel-title">Scenarios</h3>
      <button type="button" onClick={() => file.current?.click()}>
        Import JSON
      </button>
      <input ref={file} type="file" accept="application/json,.json" hidden aria-label="Import scenario file" onChange={(e) => void onFile(e.target.files?.[0])} />
      {isLoading && <p className="muted">Loading…</p>}
      {data && data.length === 0 && <p className="muted">No saved scenarios yet.</p>}
      <ul className="plain-list">
        {data?.map((s) => (
          <li key={s.id} className={`list-row${openId === s.id ? ' current' : ''}`}>
            {renaming?.id === s.id ? (
              <input
                aria-label="New name"
                autoFocus
                value={renaming.name}
                maxLength={120}
                onChange={(e) => setRenaming({ id: s.id, name: e.target.value })}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && renaming.name.trim()) void rename(s.id, renaming.name.trim());
                  if (e.key === 'Escape') setRenaming(null);
                }}
              />
            ) : (
              <span className="grow" title={`Updated ${s.updatedAt}`}>
                {s.name}
              </span>
            )}
            <button type="button" onClick={() => (dirty && openId !== s.id ? setPendingOpen(s.id) : void open(s.id))}>
              Open
            </button>
            <button type="button" onClick={() => setRenaming({ id: s.id, name: s.name })}>
              Rename
            </button>
            <button type="button" className="danger" onClick={() => setDeleting({ id: s.id, name: s.name })}>
              Delete
            </button>
          </li>
        ))}
      </ul>
      {pendingOpen && (
        <ConfirmDialog
          title="Open another scenario"
          message={<p>The current draft has unsaved changes. Discard them?</p>}
          confirmLabel="Discard and open"
          danger
          onCancel={() => setPendingOpen(null)}
          onConfirm={() => {
            const id = pendingOpen;
            setPendingOpen(null);
            void open(id);
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title="Delete scenario"
          message={<p>Delete "{deleting.name}"? This cannot be undone.</p>}
          confirmLabel="Delete"
          danger
          onCancel={() => setDeleting(null)}
          onConfirm={() => {
            const target = deleting;
            setDeleting(null);
            del.mutate(target.id, {
              onSuccess: () => {
                if (openId === target.id) {
                  reset();
                  exitDraft();
                }
                toast.success(`Deleted "${target.name}"`);
              },
              onError: (e) => toast.error(messageOf(e)),
            });
          }}
        />
      )}
    </div>
  );
}
